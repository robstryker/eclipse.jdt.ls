/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/ReorgCorrectionsSubProcessor.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman, mpchapman@gmail.com - 89977 Make JDT .java agnostic
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.manipulation.CUCorrectionProposalCore;
import org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore;
import org.eclipse.jdt.core.manipulation.ICleanUpFixCore;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.core.manipulation.JavaElementLabelsCore;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.corext.fix.UnusedCodeFixCore;
import org.eclipse.jdt.internal.corext.refactoring.changes.MoveCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameCompilationUnitChange;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.text.correction.IInvocationContextCore;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.internal.ui.text.correction.IProposalRelevance;
import org.eclipse.jdt.internal.ui.text.correction.proposals.CorrectMainTypeNameProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.CorrectPackageDeclarationProposalCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.lsp4j.CodeActionKind;


public class ReorgCorrectionsSubProcessor {

	public static void getWrongTypeNameProposals(IInvocationContextCore context, IProblemLocationCore problem,
			Collection<ProposalKindWrapper> proposals) {
		ICompilationUnit cu= context.getCompilationUnit();
		boolean isLinked = cu.getResource().isLinked();

		IJavaProject javaProject= cu.getJavaProject();
		String sourceLevel= javaProject.getOption(JavaCore.COMPILER_SOURCE, true);
		String compliance= javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, true);

		CompilationUnit root= context.getASTRoot();

		ASTNode coveredNode= problem.getCoveredNode(root);
		if (!(coveredNode instanceof SimpleName)) {
			return;
		}

		ASTNode parentType= coveredNode.getParent();
		if (!(parentType instanceof AbstractTypeDeclaration)) {
			return;
		}

		String currTypeName= ((SimpleName) coveredNode).getIdentifier();
		String newTypeName= JavaCore.removeJavaLikeExtension(cu.getElementName());


		boolean hasOtherPublicTypeBefore = false;

		boolean found = false;
		List<AbstractTypeDeclaration> types= root.types();
		for (int i= 0; i < types.size(); i++) {
			AbstractTypeDeclaration curr= types.get(i);
			if (parentType != curr) {
				if (newTypeName.equals(curr.getName().getIdentifier())) {
					return;
				}
				if (!found && Modifier.isPublic(curr.getModifiers())) {
					hasOtherPublicTypeBefore = true;
				}
			} else {
				found = true;
			}
		}

		if (!JavaConventions.validateJavaTypeName(newTypeName, sourceLevel, compliance).matches(IStatus.ERROR)) {
			String title = Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_renametype_description, BasicElementLabels.getJavaElementName(newTypeName));
			CorrectMainTypeNameProposalCore p = new CorrectMainTypeNameProposalCore(title, cu, null, context, currTypeName, newTypeName, IProposalRelevance.RENAME_TYPE);
			proposals.add(CodeActionHandler.wrap(p, CodeActionKind.QuickFix));
		}

		if (!hasOtherPublicTypeBefore && JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isResourceOperationSupported()) {
			String newCUName = JavaModelUtil.getRenamedCUName(cu, currTypeName);
			ICompilationUnit newCU = ((IPackageFragment) (cu.getParent())).getCompilationUnit(newCUName);
			if (!newCU.exists() && !isLinked && !JavaConventions.validateCompilationUnitName(newCUName, sourceLevel, compliance).matches(IStatus.ERROR)) {
				RenameCompilationUnitChange change = new RenameCompilationUnitChange(cu, newCUName);

				// rename CU
				String label = Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_renamecu_description, BasicElementLabels.getResourceName(newCUName));
				ChangeCorrectionProposalCore proposal = new ChangeCorrectionProposalCore(label, change, IProposalRelevance.RENAME_CU);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}
		}

	}

	public static void getWrongPackageDeclNameProposals(IInvocationContextCore context, IProblemLocationCore problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		ICompilationUnit cu= context.getCompilationUnit();

		// correct package declaration
		int relevance= cu.getPackageDeclarations().length == 0 ? IProposalRelevance.MISSING_PACKAGE_DECLARATION : IProposalRelevance.CORRECT_PACKAGE_DECLARATION; // bug 38357
		proposals.add(CodeActionHandler.wrap(new CorrectPackageDeclarationProposalCore(cu, problem, relevance), CodeActionKind.QuickFix));

		// move to package
		IPackageDeclaration[] packDecls= cu.getPackageDeclarations();
		String newPackName= packDecls.length > 0 ? packDecls[0].getElementName() : ""; //$NON-NLS-1$

		IPackageFragmentRoot root= JavaModelUtil.getPackageFragmentRoot(cu);
		IPackageFragment newPack= root.getPackageFragment(newPackName);

		ICompilationUnit newCU= newPack.getCompilationUnit(cu.getElementName());
		boolean isLinked= cu.getResource().isLinked();
		if (!newCU.exists() && !isLinked) {
			String label;
			if (newPack.isDefaultPackage()) {
				label= Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_movecu_default_description, BasicElementLabels.getFileName(cu));
			} else {
				String packageLabel= JavaElementLabelsCore.getElementLabel(newPack, JavaElementLabelsCore.ALL_DEFAULT);
				label= Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_movecu_description, new Object[] { BasicElementLabels.getFileName(cu), packageLabel });
			}

			ChangeCorrectionProposalCore p = new ChangeCorrectionProposalCore(label, new MoveCompilationUnitChange(cu, newPack), IProposalRelevance.MOVE_CU_TO_PACKAGE);
			proposals.add(CodeActionHandler.wrap(p, CodeActionKind.QuickFix));
		}
	}

	public static void removeImportStatementProposals(IInvocationContextCore context, IProblemLocationCore problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		IProposableFix fix = UnusedCodeFixCore.createRemoveUnusedImportFix(context.getASTRoot(), problem);
		if (fix != null) {
			try {
				CompilationUnitChange change = fix.createChange(null);
				CUCorrectionProposalCore proposal = new CUCorrectionProposalCore(change.getName(), change.getCompilationUnit(),
						change, IProposalRelevance.REMOVE_UNUSED_IMPORT);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}
		ICleanUpFixCore removeAllUnusedImportsFix = UnusedCodeFixCore.createCleanUp(context.getASTRoot(), false, false, false, false, false, true, false, false);
		if (removeAllUnusedImportsFix != null) {
			CompilationUnitChange change = removeAllUnusedImportsFix.createChange(null);
			CUCorrectionProposalCore proposal = new CUCorrectionProposalCore(CorrectionMessages.ReorgCorrectionsSubProcessor_remove_all_unused_imports, change.getCompilationUnit(),
				change, IProposalRelevance.REMOVE_UNUSED_IMPORT);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}
	}
}
