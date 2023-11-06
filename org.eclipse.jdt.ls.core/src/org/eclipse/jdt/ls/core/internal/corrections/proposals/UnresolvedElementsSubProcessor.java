/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/UnresolvedElementsSubProcessor.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Renaud Waldura &lt;renaud+eclipse@waldura.com&gt; - New class/interface with wizard
 *     Rabea Gransberger <rgransberger@gmx.de> - [quick fix] Fix several visibility issues - https://bugs.eclipse.org/394692
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.manipulation.CodeStyleConfiguration;
import org.eclipse.jdt.core.manipulation.TypeKinds;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.core.manipulation.BindingLabelProviderCore;
import org.eclipse.jdt.internal.core.manipulation.JavaElementLabelsCore;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.text.correction.IInvocationContextCore;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.internal.ui.text.correction.IProposalRelevance;
import org.eclipse.jdt.internal.ui.text.correction.proposals.AddArgumentCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.AddTypeParameterProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.CastCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore.ChangeDescription;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore.EditDescription;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore.InsertDescription;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore.RemoveDescription;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore.SwapDescription;
import org.eclipse.jdt.internal.ui.text.correction.proposals.NewAnnotationMemberProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.NewMethodCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.NewVariableCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.RenameNodeCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ReplaceCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.contentassist.TypeFilter;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.NameMatcher;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.corrections.SimilarElement;
import org.eclipse.jdt.ls.core.internal.corrections.SimilarElementsRequestor;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.lsp4j.CodeActionKind;


public class UnresolvedElementsSubProcessor {

	public static void getVariableProposals(IInvocationContextCore context, IProblemLocationCore problem,
			IVariableBinding resolvedField, Collection<ProposalKindWrapper> proposals) throws CoreException {

		ICompilationUnit cu= context.getCompilationUnit();

		CompilationUnit astRoot= context.getASTRoot();
		ASTNode selectedNode= problem.getCoveredNode(astRoot);
		if (selectedNode == null) {
			return;
		}

		// type that defines the variable
		ITypeBinding binding= null;
		ITypeBinding declaringTypeBinding= Bindings.getBindingOfParentTypeContext(selectedNode);
		if (declaringTypeBinding == null) {
			return;
		}

		// possible type kind of the node
		boolean suggestVariableProposals= true;
		int typeKind= 0;

		while (selectedNode instanceof ParenthesizedExpression parenthesizedExpression) {
			selectedNode = parenthesizedExpression.getExpression();
		}


		Name node= null;

		switch (selectedNode.getNodeType()) {
		case ASTNode.SIMPLE_NAME:
			node= (SimpleName) selectedNode;
			ASTNode parent= node.getParent();
			StructuralPropertyDescriptor locationInParent= node.getLocationInParent();
			if (locationInParent == ExpressionMethodReference.EXPRESSION_PROPERTY) {
				typeKind= TypeKinds.REF_TYPES;
			} else if (locationInParent == MethodInvocation.EXPRESSION_PROPERTY) {
				if (JavaModelUtil.is1d8OrHigher(cu.getJavaProject())) {
					typeKind= TypeKinds.CLASSES | TypeKinds.INTERFACES | TypeKinds.ENUMS;
				} else {
					typeKind= TypeKinds.CLASSES;
				}
			} else if (locationInParent == FieldAccess.NAME_PROPERTY) {
				Expression expression= ((FieldAccess) parent).getExpression();
				if (expression != null) {
					binding= expression.resolveTypeBinding();
					if (binding == null) {
						node= null;
					}
				}
			} else if (parent instanceof SimpleType || parent instanceof NameQualifiedType) {
				suggestVariableProposals= false;
				typeKind= TypeKinds.REF_TYPES_AND_VAR;
			} else if (parent instanceof QualifiedName name) {
				Name qualifier = name.getQualifier();
				if (qualifier != node) {
					binding= qualifier.resolveTypeBinding();
				} else {
					typeKind= TypeKinds.REF_TYPES;
				}
				ASTNode outerParent= parent.getParent();
				while (outerParent instanceof QualifiedName) {
					outerParent= outerParent.getParent();
				}
				if (outerParent instanceof SimpleType || outerParent instanceof NameQualifiedType) {
					typeKind= TypeKinds.REF_TYPES;
					suggestVariableProposals= false;
				}

			} else if (locationInParent == SwitchCase.EXPRESSION_PROPERTY || locationInParent == SwitchCase.EXPRESSIONS2_PROPERTY) {
				ASTNode caseParent = node.getParent().getParent();
				ITypeBinding switchExp = null;
				if (caseParent instanceof SwitchStatement switchStatement) {
					switchExp = switchStatement.getExpression().resolveTypeBinding();
				} else if (caseParent instanceof SwitchExpression switchExpression) {
					switchExp = switchExpression.getExpression().resolveTypeBinding();
				}
				if (switchExp != null && switchExp.isEnum()) {
					binding= switchExp;
				}
			} else if (locationInParent == SuperFieldAccess.NAME_PROPERTY) {
				binding= declaringTypeBinding.getSuperclass();
			}
			break;
		case ASTNode.QUALIFIED_NAME:
			QualifiedName qualifierName= (QualifiedName) selectedNode;
			ITypeBinding qualifierBinding= qualifierName.getQualifier().resolveTypeBinding();
			if (qualifierBinding != null) {
				node= qualifierName.getName();
				binding= qualifierBinding;
			} else {
				node= qualifierName.getQualifier();
				typeKind= TypeKinds.REF_TYPES;
				suggestVariableProposals= node.isSimpleName();
			}
			if (selectedNode.getParent() instanceof SimpleType || selectedNode.getParent() instanceof NameQualifiedType) {
				typeKind= TypeKinds.REF_TYPES;
				suggestVariableProposals= false;
			}
			break;
		case ASTNode.FIELD_ACCESS:
			FieldAccess access= (FieldAccess) selectedNode;
			Expression expression= access.getExpression();
			if (expression != null) {
				binding= expression.resolveTypeBinding();
				if (binding != null) {
					node= access.getName();
				}
			}
			break;
		case ASTNode.SUPER_FIELD_ACCESS:
			binding= declaringTypeBinding.getSuperclass();
			node= ((SuperFieldAccess) selectedNode).getName();
			break;
		default:
		}

		if (node == null) {
			return;
		}

		// add type proposals
		if (typeKind != 0) {
			if (!JavaModelUtil.is50OrHigher(cu.getJavaProject())) {
				typeKind &= ~(TypeKinds.ANNOTATIONS | TypeKinds.ENUMS | TypeKinds.VARIABLES);
			}

			int relevance= Character.isUpperCase(ASTNodes.getSimpleNameIdentifier(node).charAt(0)) ? IProposalRelevance.VARIABLE_TYPE_PROPOSAL_1 : IProposalRelevance.VARIABLE_TYPE_PROPOSAL_2;
			addSimilarTypeProposals(context, typeKind, cu, node, relevance + 1, proposals);

			typeKind &= ~TypeKinds.ANNOTATIONS;
			addNewTypeProposals(cu, node, typeKind, relevance, proposals);
		}

		if (!suggestVariableProposals) {
			return;
		}

		SimpleName simpleName= node.isSimpleName() ? (SimpleName) node : ((QualifiedName) node).getName();
		boolean isWriteAccess= ASTResolving.isWriteAccess(node);

		// similar variables
		addSimilarVariableProposals(cu, astRoot, binding, resolvedField, simpleName, isWriteAccess, proposals);

		if (binding == null) {
			addStaticImportFavoriteProposals(context, simpleName, false, proposals);
		}

		if (resolvedField == null || binding == null || resolvedField.getDeclaringClass() != binding.getTypeDeclaration() && Modifier.isPrivate(resolvedField.getModifiers())) {

			// new fields
			addNewFieldProposals(cu, astRoot, binding, declaringTypeBinding, simpleName, isWriteAccess, proposals);

			// new parameters and local variables
			if (binding == null) {
				addNewVariableProposals(cu, node, simpleName, proposals);
			}
		}
	}

	private static void addNewVariableProposals(ICompilationUnit cu, Name node, SimpleName simpleName,
			Collection<ProposalKindWrapper> proposals) {
		String name= simpleName.getIdentifier();
		BodyDeclaration bodyDeclaration= ASTResolving.findParentBodyDeclaration(node, true);
		int type= bodyDeclaration.getNodeType();
		if (type == ASTNode.METHOD_DECLARATION) {
			int relevance= StubUtility.hasParameterName(cu.getJavaProject(), name) ? IProposalRelevance.CREATE_PARAMETER_PREFIX_OR_SUFFIX_MATCH : IProposalRelevance.CREATE_PARAMETER;
			String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_createparameter_description, BasicElementLabels.getJavaElementName(name));
			NewVariableCorrectionProposalCore proposal = new NewVariableCorrectionProposalCore(label, cu, NewVariableCorrectionProposal.PARAM, simpleName, null, relevance);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}
		if (type == ASTNode.INITIALIZER || type == ASTNode.METHOD_DECLARATION && !ASTResolving.isInsideConstructorInvocation((MethodDeclaration) bodyDeclaration, node)) {
			int relevance= StubUtility.hasLocalVariableName(cu.getJavaProject(), name) ? IProposalRelevance.CREATE_LOCAL_PREFIX_OR_SUFFIX_MATCH : IProposalRelevance.CREATE_LOCAL;
			String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_createlocal_description, BasicElementLabels.getJavaElementName(name));
			NewVariableCorrectionProposalCore proposal = new NewVariableCorrectionProposalCore(label, cu, NewVariableCorrectionProposal.LOCAL, simpleName, null, relevance);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}

		if (node.getParent().getNodeType() == ASTNode.ASSIGNMENT) {
			Assignment assignment= (Assignment) node.getParent();
			if (assignment.getLeftHandSide() == node && assignment.getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
				ASTNode statement= assignment.getParent();
				ASTRewrite rewrite= ASTRewrite.create(statement.getAST());
				if (ASTNodes.isControlStatementBody(assignment.getParent().getLocationInParent())) {
					rewrite.replace(statement, rewrite.getAST().newBlock(), null);
				} else {
					rewrite.remove(statement, null);
				}
				String label= CorrectionMessages.UnresolvedElementsSubProcessor_removestatement_description;
				ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, cu, rewrite,
						IProposalRelevance.REMOVE_ASSIGNMENT);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}
		}
	}

	private static void addNewFieldProposals(ICompilationUnit cu, CompilationUnit astRoot, ITypeBinding binding,
			ITypeBinding declaringTypeBinding, SimpleName simpleName, boolean isWriteAccess,
			Collection<ProposalKindWrapper> proposals) throws JavaModelException {
		// new variables
		ICompilationUnit targetCU;
		ITypeBinding senderDeclBinding;
		if (binding != null) {
			senderDeclBinding= binding.getTypeDeclaration();
			targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, senderDeclBinding);
		} else { // binding is null for accesses without qualifier
			senderDeclBinding= declaringTypeBinding;
			targetCU= cu;
		}

		if (!senderDeclBinding.isFromSource() || targetCU == null) {
			return;
		}

		boolean mustBeConst= ASTResolving.isInsideModifiers(simpleName);

		addNewFieldForType(targetCU, binding, senderDeclBinding, simpleName, isWriteAccess, mustBeConst, proposals);

		if (binding == null && senderDeclBinding.isNested()) {
			ASTNode anonymDecl= astRoot.findDeclaringNode(senderDeclBinding);
			if (anonymDecl != null) {
				ITypeBinding bind= Bindings.getBindingOfParentType(anonymDecl.getParent());
				if (!bind.isAnonymous()) {
					addNewFieldForType(targetCU, bind, bind, simpleName, isWriteAccess, mustBeConst, proposals);
				}
			}
		}
	}

	private static void addNewFieldForType(ICompilationUnit targetCU, ITypeBinding binding,
			ITypeBinding senderDeclBinding, SimpleName simpleName, boolean isWriteAccess, boolean mustBeConst,
			Collection<ProposalKindWrapper> proposals) {
		String name= simpleName.getIdentifier();
		String nameLabel= BasicElementLabels.getJavaElementName(name);
		String label;
		if (senderDeclBinding.isEnum() && !isWriteAccess) {
			label = Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_createenum_description,
					new Object[] { nameLabel, ASTResolving.getTypeSignature(senderDeclBinding) });
			NewVariableCorrectionProposalCore proposal = new NewVariableCorrectionProposalCore(label, targetCU, NewVariableCorrectionProposal.ENUM_CONST, simpleName, senderDeclBinding, 10);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		} else {
			if (!mustBeConst) {
				if (binding == null) {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_createfield_description, nameLabel);
				} else {
					label = Messages.format(
							CorrectionMessages.UnresolvedElementsSubProcessor_createfield_other_description,
							new Object[] { nameLabel, ASTResolving.getTypeSignature(senderDeclBinding) });
				}
				int fieldRelevance= StubUtility.hasFieldName(targetCU.getJavaProject(), name) ? IProposalRelevance.CREATE_FIELD_PREFIX_OR_SUFFIX_MATCH : IProposalRelevance.CREATE_FIELD;
				NewVariableCorrectionProposalCore proposal = new NewVariableCorrectionProposalCore(label, targetCU, NewVariableCorrectionProposal.FIELD, simpleName, senderDeclBinding, fieldRelevance);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}

			if (!isWriteAccess && !senderDeclBinding.isAnonymous()) {
				if (binding == null) {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_createconst_description, nameLabel);
				} else {
					label = Messages.format(
							CorrectionMessages.UnresolvedElementsSubProcessor_createconst_other_description,
							new Object[] { nameLabel, ASTResolving.getTypeSignature(senderDeclBinding) });
				}
				int constRelevance= StubUtility.hasConstantName(targetCU.getJavaProject(), name) ? IProposalRelevance.CREATE_CONSTANT_PREFIX_OR_SUFFIX_MATCH : IProposalRelevance.CREATE_CONSTANT;
				NewVariableCorrectionProposalCore proposal = new NewVariableCorrectionProposalCore(label, targetCU, NewVariableCorrectionProposal.CONST_FIELD, simpleName, senderDeclBinding, constRelevance);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}
		}
	}

	private static void addSimilarVariableProposals(ICompilationUnit cu, CompilationUnit astRoot, ITypeBinding binding,
			IVariableBinding resolvedField, SimpleName node, boolean isWriteAccess,
			Collection<ProposalKindWrapper> proposals) {
		int kind= ScopeAnalyzer.VARIABLES | ScopeAnalyzer.CHECK_VISIBILITY;
		if (!isWriteAccess) {
			kind |= ScopeAnalyzer.METHODS; // also try to find similar methods
		}

		IBinding[] varsAndMethodsInScope= (new ScopeAnalyzer(astRoot)).getDeclarationsInScope(node, kind);
		if (varsAndMethodsInScope.length > 0) {
			// avoid corrections like int i= i;
			String otherNameInAssign= null;

			// help with x.getString() -> y.getString()
			String methodSenderName= null;
			String fieldSenderName= null;

			ASTNode parent= node.getParent();
			switch (parent.getNodeType()) {
			case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
				// node must be initializer
				otherNameInAssign= ((VariableDeclarationFragment) parent).getName().getIdentifier();
				break;
			case ASTNode.ASSIGNMENT:
				Assignment assignment= (Assignment) parent;
				if (isWriteAccess && assignment.getRightHandSide() instanceof SimpleName name) {
					otherNameInAssign = name.getIdentifier();
				} else if (!isWriteAccess && assignment.getLeftHandSide() instanceof SimpleName name) {
					otherNameInAssign = name.getIdentifier();
				}
				break;
			case ASTNode.METHOD_INVOCATION:
				MethodInvocation inv= (MethodInvocation) parent;
				if (inv.getExpression() == node) {
					methodSenderName= inv.getName().getIdentifier();
				}
				break;
			case ASTNode.QUALIFIED_NAME:
				QualifiedName qualName= (QualifiedName) parent;
				if (qualName.getQualifier() == node) {
					fieldSenderName= qualName.getName().getIdentifier();
				}
				break;
			}


			ITypeBinding guessedType= ASTResolving.guessBindingForReference(node);

			ITypeBinding objectBinding= astRoot.getAST().resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
			String identifier= node.getIdentifier();
			boolean isInStaticContext= ASTResolving.isInStaticContext(node);
			ArrayList<ProposalKindWrapper> newProposals = new ArrayList<>(51);

			loop: for (int i= 0; i < varsAndMethodsInScope.length && newProposals.size() <= 50; i++) {
				IBinding varOrMeth= varsAndMethodsInScope[i];
				if (varOrMeth instanceof IVariableBinding curr) {
					String currName= curr.getName();
					if (currName.equals(otherNameInAssign)) {
						continue loop;
					}
					if (resolvedField != null && Bindings.equals(resolvedField, curr)) {
						continue loop;
					}
					boolean isFinal= Modifier.isFinal(curr.getModifiers());
					if (isFinal && curr.isField() && isWriteAccess) {
						continue loop;
					}
					if (isInStaticContext && !Modifier.isStatic(curr.getModifiers()) && curr.isField()) {
						continue loop;
					}

					int relevance= IProposalRelevance.SIMILAR_VARIABLE_PROPOSAL;
					if (NameMatcher.isSimilarName(currName, identifier)) {
						relevance += 3; // variable with a similar name than the unresolved variable
					}
					if (currName.equalsIgnoreCase(identifier)) {
						relevance+= 5;
					}
					ITypeBinding varType= curr.getType();
					if (varType != null) {
						if (guessedType != null && guessedType != objectBinding) { // too many result with object
							// variable type is compatible with the guessed type
							if (!isWriteAccess && canAssign(varType, guessedType)
									|| isWriteAccess && canAssign(guessedType, varType)) {
								relevance += 2; // unresolved variable can be assign to this variable
							}
						}
						if (methodSenderName != null && hasMethodWithName(varType, methodSenderName)) {
							relevance += 2;
						}
						if (fieldSenderName != null && hasFieldWithName(varType, fieldSenderName)) {
							relevance += 2;
						}
					}

					if (relevance > 0) {
						String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_changevariable_description, BasicElementLabels.getJavaElementName(currName));
						RenameNodeCorrectionProposalCore proposal = new RenameNodeCorrectionProposalCore(label, cu, node.getStartPosition(), node.getLength(), currName, relevance);
						newProposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
					}
				} else if (varOrMeth instanceof IMethodBinding curr) {
					if (!curr.isConstructor() && guessedType != null && canAssign(curr.getReturnType(), guessedType)) {
						if (NameMatcher.isSimilarName(curr.getName(), identifier)) {
							AST ast= astRoot.getAST();
							ASTRewrite rewrite= ASTRewrite.create(ast);
							String label = Messages.format(
									CorrectionMessages.UnresolvedElementsSubProcessor_changetomethod_description,
									ASTResolving.getMethodSignature(curr));
							ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, cu, rewrite,
									IProposalRelevance.CHANGE_TO_METHOD);
							newProposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));

							MethodInvocation newInv= ast.newMethodInvocation();
							newInv.setName(ast.newSimpleName(curr.getName()));
							ITypeBinding[] parameterTypes= curr.getParameterTypes();
							for (int k= 0; k < parameterTypes.length; k++) {
								ASTNode arg= ASTNodeFactory.newDefaultExpression(ast, parameterTypes[k]);
								newInv.arguments().add(arg);
							}
							rewrite.replace(node, newInv, null);
						}
					}
				}
			}
			if (newProposals.size() <= 50) {
				proposals.addAll(newProposals);
			}
		}
		if (binding != null && binding.isArray()) {
			String idLength= "length"; //$NON-NLS-1$
			String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_changevariable_description, idLength);
			RenameNodeCorrectionProposalCore proposal = new RenameNodeCorrectionProposalCore(label, cu, node.getStartPosition(), node.getLength(), idLength, IProposalRelevance.CHANGE_VARIABLE);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}
	}

	private static boolean canAssign(ITypeBinding returnType, ITypeBinding guessedType) {
		return returnType.isAssignmentCompatible(guessedType);
	}

	private static boolean hasMethodWithName(ITypeBinding typeBinding, String name) {
		IVariableBinding[] fields= typeBinding.getDeclaredFields();
		for (int i= 0; i < fields.length; i++) {
			if (fields[i].getName().equals(name)) {
				return true;
			}
		}
		ITypeBinding superclass= typeBinding.getSuperclass();
		if (superclass != null) {
			return hasMethodWithName(superclass, name);
		}
		return false;
	}

	private static boolean hasFieldWithName(ITypeBinding typeBinding, String name) {
		IMethodBinding[] methods= typeBinding.getDeclaredMethods();
		for (int i= 0; i < methods.length; i++) {
			if (methods[i].getName().equals(name)) {
				return true;
			}
		}
		ITypeBinding superclass= typeBinding.getSuperclass();
		if (superclass != null) {
			return hasMethodWithName(superclass, name);
		}
		return false;
	}

	private static int evauateTypeKind(ASTNode node, IJavaProject project) {
		int kind = ASTResolving.getPossibleTypeKinds(node, JavaModelUtil.is50OrHigher(project));
		return kind;
	}


	public static void getTypeProposals(IInvocationContextCore context, IProblemLocationCore problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		ICompilationUnit cu= context.getCompilationUnit();
		ASTNode selectedNode;
		if (problem.getProblemId() == IProblem.UndefinedType && cu.getBuffer() != null && cu.getBuffer().getChar(problem.getOffset()) == '@') {
			int offset = problem.getOffset() + 1;
			int length = problem.getLength() - 1;
			while (offset < cu.getBuffer().getLength() && length >= 0 && Character.isWhitespace(cu.getBuffer().getChar(offset))) {
				offset++;
				length--;
			}
			NodeFinder finder = new NodeFinder(context.getASTRoot(), offset, length);
			selectedNode = finder.getCoveringNode();
		} else {
			selectedNode = problem.getCoveringNode(context.getASTRoot());
		}
		if (selectedNode == null) {
			return;
		}

		int kind= evauateTypeKind(selectedNode, cu.getJavaProject());

		if (kind == TypeKinds.REF_TYPES) {
			addEnhancedForWithoutTypeProposals(cu, selectedNode, proposals);
		}

		while (selectedNode.getLocationInParent() == QualifiedName.NAME_PROPERTY) {
			selectedNode= selectedNode.getParent();
		}

		Name node= null;
		if (selectedNode instanceof Annotation annotation) {
			selectedNode = annotation.getTypeName();
		}
		if (selectedNode instanceof SimpleType simpleType) {
			node = simpleType.getName();
		} else if (selectedNode instanceof NameQualifiedType type) {
			node = type.getName();
		} else if (selectedNode instanceof ArrayType type) {
			Type elementType = type.getElementType();
			if (elementType.isSimpleType()) {
				node= ((SimpleType) elementType).getName();
			} else if (elementType.isNameQualifiedType()) {
				node= ((NameQualifiedType) elementType).getName();
			} else {
				return;
			}
		} else if (selectedNode instanceof Name name) {
			node = name;
		} else {
			return;
		}

		// change to similar type proposals
		addSimilarTypeProposals(context, kind, cu, node, IProposalRelevance.SIMILAR_TYPE, proposals);

		while (node.getParent() instanceof QualifiedName parentName) {
			node = parentName;
		}

		if (selectedNode != node) {
			kind= evauateTypeKind(node, cu.getJavaProject());
		}
		if ((kind & (TypeKinds.CLASSES | TypeKinds.INTERFACES)) != 0) {
			kind &= ~TypeKinds.ANNOTATIONS; // only propose annotations when there are no other suggestions
		}
		addNewTypeProposals(cu, node, kind, IProposalRelevance.NEW_TYPE, proposals);
	}

	private static void addEnhancedForWithoutTypeProposals(ICompilationUnit cu, ASTNode selectedNode,
			Collection<ProposalKindWrapper> proposals) {
		if (selectedNode instanceof SimpleName simpleName && (selectedNode.getLocationInParent() == SimpleType.NAME_PROPERTY || selectedNode.getLocationInParent() == NameQualifiedType.NAME_PROPERTY)) {
			ASTNode type= selectedNode.getParent();
			if (type.getLocationInParent() == SingleVariableDeclaration.TYPE_PROPERTY) {
				SingleVariableDeclaration svd= (SingleVariableDeclaration) type.getParent();
				if (svd.getLocationInParent() == EnhancedForStatement.PARAMETER_PROPERTY) {
					if (svd.getName().getLength() == 0) {
						String name= simpleName.getIdentifier();
						int relevance= StubUtility.hasLocalVariableName(cu.getJavaProject(), name) ? 10 : 7;
						String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_create_loop_variable_description, BasicElementLabels.getJavaElementName(name));

						NewVariableCorrectionProposalCore proposal = new NewVariableCorrectionProposalCore(label, cu, NewVariableCorrectionProposal.LOCAL, simpleName, null, relevance);
						proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
					}
				}
			}
		}
	}

	static CompilationUnitChange createAddImportChange(ICompilationUnit cu, Name name, String fullyQualifiedName) throws CoreException {
		String[] args= { BasicElementLabels.getJavaElementName(Signature.getSimpleName(fullyQualifiedName)),
				BasicElementLabels.getJavaElementName(Signature.getQualifier(fullyQualifiedName)) };
		String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_importtype_description, args);

		CompilationUnitChange cuChange= new CompilationUnitChange(label, cu);
		ImportRewrite importRewrite = CodeStyleConfiguration.createImportRewrite((CompilationUnit) name.getRoot(), true);
		importRewrite.addImport(fullyQualifiedName);
		cuChange.setEdit(importRewrite.rewriteImports(null));
		return cuChange;
	}

	private static void addSimilarTypeProposals(IInvocationContextCore context, int kind, ICompilationUnit cu, Name node, int relevance,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		SimilarElement[] elements = SimilarElementsRequestor.findSimilarElement(cu, node, kind);

		// try to resolve type in context -> highest severity
		String resolvedTypeName= null;
		ITypeBinding binding= ASTResolving.guessBindingForTypeReference(node);
		if (binding != null) {
			ITypeBinding simpleBinding= binding;
			if (simpleBinding.isArray()) {
				simpleBinding= simpleBinding.getElementType();
			}
			simpleBinding= simpleBinding.getTypeDeclaration();

			if (!simpleBinding.isRecovered()) {
				resolvedTypeName= simpleBinding.getQualifiedName();
				ProposalKindWrapper proposal = createTypeRefChangeProposal(cu, resolvedTypeName, node, relevance + 2, elements.length);
				proposals.add(proposal);
				if (proposal.getProposal() instanceof AddImportCorrectionProposal) {
					proposal.getProposal().setRelevance(relevance + elements.length + 2);
				}

				if (binding.isParameterizedType()
						&& (node.getParent() instanceof SimpleType || node.getParent() instanceof NameQualifiedType)
						&& !(node.getParent().getParent() instanceof Type)) {
					proposals.add(createTypeRefChangeFullProposal(cu, binding, node, relevance + 5));
				}
			}
		} else {
			ASTNode normalizedNode= ASTNodes.getNormalizedNode(node);
			if (!(normalizedNode.getParent() instanceof Type) && node.getParent() != normalizedNode) {
				ITypeBinding normBinding= ASTResolving.guessBindingForTypeReference(normalizedNode);
				if (normBinding != null && !normBinding.isRecovered()) {
					proposals.add(createTypeRefChangeFullProposal(cu, normBinding, normalizedNode, relevance + 5));
				}
			}
		}

		// add all similar elements
		for (int i= 0; i < elements.length; i++) {
			SimilarElement elem= elements[i];
			if ((elem.getKind() & TypeKinds.ALL_TYPES) != 0) {
				String fullName= elem.getName();
				if (!fullName.equals(resolvedTypeName)) {
					proposals.add(createTypeRefChangeProposal(cu, fullName, node, relevance, elements.length));
				}
			}
		}
	}

	private static ProposalKindWrapper createTypeRefChangeProposal(ICompilationUnit cu, String fullName, Name node, int relevance, int maxProposals) {
		ImportRewrite importRewrite= null;
		String simpleName= fullName;
		String packName= Signature.getQualifier(fullName);
		if (packName.length() > 0) { // no imports for primitive types, type variables
			importRewrite = CodeStyleConfiguration.createImportRewrite((CompilationUnit) node.getRoot(), true);
			BodyDeclaration scope= ASTResolving.findParentBodyDeclaration(node); // can be null in package-info.java
			ImportRewriteContext context= new ContextSensitiveImportRewriteContext(scope != null ? scope : node, importRewrite);
			simpleName= importRewrite.addImport(fullName, context);
		}

		if (!isLikelyTypeName(simpleName)) {
			relevance -= 2;
		}

		ASTRewriteCorrectionProposalCore proposal;
		if (importRewrite != null && node.isSimpleName() && simpleName.equals(((SimpleName) node).getIdentifier())) { // import only
			// import only
			String[] arg= { BasicElementLabels.getJavaElementName(simpleName), BasicElementLabels.getJavaElementName(packName) };
			String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_importtype_description, arg);
			proposal = new AddImportCorrectionProposal(label, cu, relevance + 100, packName, simpleName,
					(SimpleName) node);
		} else {
			String label;
			if (packName.length() == 0) {
				label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_changetype_nopack_description, BasicElementLabels.getJavaElementName(simpleName));
			} else {
				String[] arg= { BasicElementLabels.getJavaElementName(simpleName), BasicElementLabels.getJavaElementName(packName) };
				label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_changetype_description, arg);
			}
			ASTRewrite rewrite= ASTRewrite.create(node.getAST());
			rewrite.replace(node, rewrite.createStringPlaceholder(simpleName, ASTNode.SIMPLE_TYPE), null);
			proposal = new ASTRewriteCorrectionProposalCore(label, cu, rewrite, relevance);
		}
		if (importRewrite != null) {
			proposal.setImportRewrite(importRewrite);
		}
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	static ProposalKindWrapper createTypeRefChangeFullProposal(ICompilationUnit cu, ITypeBinding binding, ASTNode node, int relevance) {
		ASTRewrite rewrite= ASTRewrite.create(node.getAST());
		String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_change_full_type_description, BindingLabelProviderCore.getBindingLabel(binding, JavaElementLabelsCore.ALL_DEFAULT));

		ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, cu, rewrite, relevance);

		ImportRewrite imports= proposal.createImportRewrite((CompilationUnit) node.getRoot());
		Type type= imports.addImport(binding, node.getAST());

		rewrite.replace(node, type, null);
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	private static boolean isLikelyTypeName(String name) {
		return name.length() > 0 && Character.isUpperCase(name.charAt(0));
	}

	private static boolean isLikelyPackageName(String name) {
		if (name.length() != 0) {
			int i= 0;
			do {
				if (Character.isUpperCase(name.charAt(i))) {
					return false;
				}
				i= name.indexOf('.', i) + 1;
			} while (i != 0 && i < name.length());
		}
		return true;
	}

	private static boolean isLikelyTypeParameterName(String name) {
		return name.length() == 1 && Character.isUpperCase(name.charAt(0));
	}

	private static boolean isLikelyMethodTypeParameterName(String name) {
		if (name.length() == 1) {
			switch (name.charAt(0)) {
			case 'S':
			case 'T':
			case 'U':
				return true;
			}
		}
		return false;
	}

	public static void addNewTypeProposals(ICompilationUnit cu, Name refNode, int kind, int relevance,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		Name node= refNode;
		do {
			String typeName = ASTNodes.getSimpleNameIdentifier(node);
			Name qualifier = null;
			// only propose to create types for qualifiers when the name starts with upper case
			boolean isPossibleName = isLikelyTypeName(typeName) || node == refNode;
			if (isPossibleName) {
				IPackageFragment enclosingPackage = null;
				IType enclosingType = null;
				if (node.isSimpleName()) {
					enclosingPackage = (IPackageFragment) cu.getParent();
					// don't suggest member type, user can select it in wizard
				} else {
					Name qualifierName = ((QualifiedName) node).getQualifier();
					IBinding binding = qualifierName.resolveBinding();
					if (binding != null && binding.isRecovered()) {
						binding = null;
					}
					if (binding instanceof ITypeBinding typeBinding) {
						enclosingType = (IType) typeBinding.getJavaElement();
					} else if (binding instanceof IPackageBinding packageBinding) {
						qualifier = qualifierName;
						enclosingPackage = (IPackageFragment) packageBinding.getJavaElement();
					} else {
						IJavaElement[] res = cu.codeSelect(qualifierName.getStartPosition(), qualifierName.getLength());
						if (res != null && res.length > 0 && res[0] instanceof IType type) {
							enclosingType = type;
						} else {
							qualifier = qualifierName;
							enclosingPackage = JavaModelUtil.getPackageFragmentRoot(cu).getPackageFragment(ASTResolving.getFullName(qualifierName));
						}
					}
				}
				int rel = relevance;
				if (enclosingPackage != null && isLikelyPackageName(enclosingPackage.getElementName())) {
					rel += 3;
				}

				if (enclosingPackage != null && !enclosingPackage.getCompilationUnit(typeName + JavaModelUtil.DEFAULT_CU_SUFFIX).exists()
						|| enclosingType != null && !enclosingType.isReadOnly() && !enclosingType.getType(typeName).exists()) { // new member type
					IJavaElement enclosing = enclosingPackage != null ? (IJavaElement) enclosingPackage : enclosingType;

					if ((kind & TypeKinds.CLASSES) != 0) {
						NewCUProposal proposal = new NewCUProposal(cu, node, NewCUProposal.K_CLASS, enclosing, rel + 3);
						proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
					}
					if ((kind & TypeKinds.INTERFACES) != 0) {
						NewCUProposal proposal = new NewCUProposal(cu, node, NewCUProposal.K_INTERFACE, enclosing, rel + 3);
						proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
					}
					if ((kind & TypeKinds.ENUMS) != 0) {
						NewCUProposal proposal = new NewCUProposal(cu, node, NewCUProposal.K_ENUM, enclosing, rel + 3);
						proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
					}
					if ((kind & TypeKinds.ANNOTATIONS) != 0) {
						NewCUProposal proposal = new NewCUProposal(cu, node, NewCUProposal.K_ANNOTATION, enclosing, rel + 3);
						proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
						// TODO: addNullityAnnotationTypesProposals(cu, node, proposals);
					}
				}
			}
			node = qualifier;
		} while (node != null);

		// type parameter proposals
		if (refNode.isSimpleName() && (kind & TypeKinds.VARIABLES)  != 0) {
			CompilationUnit root= (CompilationUnit) refNode.getRoot();
			String name= ((SimpleName) refNode).getIdentifier();
			BodyDeclaration declaration= ASTResolving.findParentBodyDeclaration(refNode);
			int baseRel= relevance;
			if (isLikelyTypeParameterName(name)) {
				baseRel += 8;
			}
			while (declaration != null) {
				IBinding binding= null;
				int rel= baseRel;
				if (declaration instanceof MethodDeclaration methodDeclaration) {
					binding = methodDeclaration.resolveBinding();
					if (isLikelyMethodTypeParameterName(name)) {
						rel+= 2;
					}
				} else if (declaration instanceof TypeDeclaration typeDeclaration) {
					binding = typeDeclaration.resolveBinding();
					rel++;
				}
				if (binding != null) {
					AddTypeParameterProposalCore proposal = new AddTypeParameterProposalCore(cu, binding, root, name, null, rel);
					proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
				}
				if (!Modifier.isStatic(declaration.getModifiers())) {
					declaration= ASTResolving.findParentBodyDeclaration(declaration.getParent());
				} else {
					declaration= null;
				}
			}
		}
	}

	public static void getMethodProposals(IInvocationContextCore context, IProblemLocationCore problem,
			boolean isOnlyParameterMismatch, Collection<ProposalKindWrapper> proposals) throws CoreException {

		ICompilationUnit cu= context.getCompilationUnit();

		CompilationUnit astRoot= context.getASTRoot();
		ASTNode selectedNode= problem.getCoveringNode(astRoot);

		if (!(selectedNode instanceof SimpleName)) {
			return;
		}
		SimpleName nameNode= (SimpleName) selectedNode;

		List<Expression> arguments;
		Expression sender;
		boolean isSuperInvocation;

		ASTNode invocationNode= nameNode.getParent();
		if (invocationNode instanceof MethodInvocation methodImpl) {
			arguments= methodImpl.arguments();
			sender= methodImpl.getExpression();
			isSuperInvocation= false;
		} else if (invocationNode instanceof SuperMethodInvocation methodImpl) {
			arguments= methodImpl.arguments();
			sender= methodImpl.getQualifier();
			isSuperInvocation= true;
		} else {
			return;
		}

		String methodName= nameNode.getIdentifier();
		int nArguments= arguments.size();

		// corrections
		IBinding[] bindings= (new ScopeAnalyzer(astRoot)).getDeclarationsInScope(nameNode, ScopeAnalyzer.METHODS);

		HashSet<String> suggestedRenames= new HashSet<>();
		for (int i= 0; i < bindings.length; i++) {
			IMethodBinding binding= (IMethodBinding) bindings[i];
			String curr= binding.getName();
			if (!curr.equals(methodName) && binding.getParameterTypes().length == nArguments && NameMatcher.isSimilarName(methodName, curr) && suggestedRenames.add(curr)) {
				String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_changemethod_description, BasicElementLabels.getJavaElementName(curr));
				RenameNodeCorrectionProposalCore proposal = new RenameNodeCorrectionProposalCore(label, context.getCompilationUnit(), problem.getOffset(), problem.getLength(), curr, IProposalRelevance.CHANGE_METHOD);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}
		}
		suggestedRenames= null;

		if (isOnlyParameterMismatch) {
			ArrayList<IMethodBinding> parameterMismatchs= new ArrayList<>();
			for (int i= 0; i < bindings.length; i++) {
				IMethodBinding binding= (IMethodBinding) bindings[i];
				if (binding.getName().equals(methodName)) {
					parameterMismatchs.add(binding);
				}
			}
			addParameterMissmatchProposals(context, problem, parameterMismatchs, invocationNode, arguments, proposals);
		}

		if (sender == null) {
			addStaticImportFavoriteProposals(context, nameNode, true, proposals);
		}

		// new method
		addNewMethodProposals(cu, astRoot, sender, arguments, isSuperInvocation, invocationNode, methodName, proposals);

		if (!isOnlyParameterMismatch && !isSuperInvocation && sender != null) {
			addMissingCastParentsProposal(cu, (MethodInvocation) invocationNode, proposals);
		}

		if (!isSuperInvocation && sender == null && invocationNode.getParent() instanceof ThrowStatement) {
			String str= "new ";   //$NON-NLS-1$ // do it the manual way, copting all the arguments is nasty
			String label= CorrectionMessages.UnresolvedElementsSubProcessor_addnewkeyword_description;
			int relevance= Character.isUpperCase(methodName.charAt(0)) ? IProposalRelevance.ADD_NEW_KEYWORD_UPPERCASE : IProposalRelevance.ADD_NEW_KEYWORD;
			ReplaceCorrectionProposalCore proposal = new ReplaceCorrectionProposalCore(label, cu, invocationNode.getStartPosition(), 0, str, relevance);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}

	}

	private static void addStaticImportFavoriteProposals(IInvocationContextCore context, SimpleName node, boolean isMethod,
			Collection<ProposalKindWrapper> proposals) throws JavaModelException {
		IJavaProject project= context.getCompilationUnit().getJavaProject();
		if (JavaModelUtil.is50OrHigher(project)) {
			String[] favourites = PreferenceManager.getPrefs(context.getCompilationUnit().getResource())
					.getJavaCompletionFavoriteMembers();
			if (favourites.length == 0) {
				return;
			}

			CompilationUnit root= context.getASTRoot();
			AST ast= root.getAST();

			String name= node.getIdentifier();
			String[] staticImports= SimilarElementsRequestor.getStaticImportFavorites(context.getCompilationUnit(), name, isMethod, favourites);
			for (int i= 0; i < staticImports.length; i++) {
				String curr= staticImports[i];

				ImportRewrite importRewrite = CodeStyleConfiguration.createImportRewrite(root, true);
				ASTRewrite astRewrite= ASTRewrite.create(ast);

				String label;
				String qualifiedTypeName= Signature.getQualifier(curr);
				String elementLabel= BasicElementLabels.getJavaElementName(JavaModelUtil.concatenateName(Signature.getSimpleName(qualifiedTypeName), name));

				String res= importRewrite.addStaticImport(qualifiedTypeName, name, isMethod, new ContextSensitiveImportRewriteContext(root, node.getStartPosition(), importRewrite));
				int dot= res.lastIndexOf('.');
				if (dot != -1) {
					String usedTypeName= importRewrite.addImport(qualifiedTypeName);
					Name newName= ast.newQualifiedName(ast.newName(usedTypeName), ast.newSimpleName(name));
					astRewrite.replace(node, newName, null);
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_change_to_static_import_description, elementLabel);
				} else {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_add_static_import_description, elementLabel);
				}

				ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label,
						context.getCompilationUnit(), astRewrite, IProposalRelevance.ADD_STATIC_IMPORT);
				proposal.setImportRewrite(importRewrite);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}
		}
	}


	private static void addNewMethodProposals(ICompilationUnit cu, CompilationUnit astRoot, Expression sender,
			List<Expression> arguments, boolean isSuperInvocation, ASTNode invocationNode, String methodName,
			Collection<ProposalKindWrapper> proposals) throws JavaModelException {
		ITypeBinding nodeParentType= Bindings.getBindingOfParentType(invocationNode);
		ITypeBinding binding= null;
		if (sender != null) {
			binding= sender.resolveTypeBinding();
		} else {
			binding= nodeParentType;
			if (isSuperInvocation && binding != null) {
				binding= binding.getSuperclass();
			}
		}
		if (binding != null && binding.isFromSource()) {
			ITypeBinding senderDeclBinding= binding.getTypeDeclaration();

			ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, senderDeclBinding);
			if (targetCU != null) {
				String label;
				ITypeBinding[] parameterTypes= getParameterTypes(arguments);
				if (parameterTypes != null) {
					String sig = ASTResolving.getMethodSignature(methodName, parameterTypes, false);
					boolean is1d8OrHigher= JavaModelUtil.is1d8OrHigher(targetCU.getJavaProject());

					boolean isSenderBindingInterface= senderDeclBinding.isInterface();
					if (nodeParentType == senderDeclBinding) {
						label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_createmethod_description, sig);
					} else {
						label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_createmethod_other_description, new Object[] { sig, BasicElementLabels.getJavaElementName(senderDeclBinding.getName()) } );
					}
					if (is1d8OrHigher || !isSenderBindingInterface
							|| (nodeParentType != senderDeclBinding && (!(sender instanceof SimpleName) || !((SimpleName) sender).getIdentifier().equals(senderDeclBinding.getName())))) {
						NewMethodCorrectionProposalCore proposal = new NewMethodCorrectionProposalCore(label, targetCU, invocationNode, arguments, senderDeclBinding, IProposalRelevance.CREATE_METHOD);
						proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
					}

					if (senderDeclBinding.isNested() && cu.equals(targetCU) && sender == null && Bindings.findMethodInHierarchy(senderDeclBinding, methodName, (ITypeBinding[]) null) == null) { // no covering method
						ASTNode anonymDecl= astRoot.findDeclaringNode(senderDeclBinding);
						if (anonymDecl != null) {
							senderDeclBinding= Bindings.getBindingOfParentType(anonymDecl.getParent());
							isSenderBindingInterface= senderDeclBinding.isInterface();
							if (!senderDeclBinding.isAnonymous()) {
								if (is1d8OrHigher || !isSenderBindingInterface) {
									String[] args = new String[] { sig,
											ASTResolving.getTypeSignature(senderDeclBinding) };
									label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_createmethod_other_description, args);
									NewMethodCorrectionProposalCore proposal = new NewMethodCorrectionProposalCore(label, targetCU, invocationNode, arguments, senderDeclBinding, IProposalRelevance.CREATE_METHOD);
									proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
								}
							}
						}
					}
				}
			}
		}
	}

	private static void addMissingCastParentsProposal(ICompilationUnit cu, MethodInvocation invocationNode,
			Collection<ProposalKindWrapper> proposals) {
		Expression sender= invocationNode.getExpression();
		if (sender instanceof ThisExpression) {
			return;
		}

		ITypeBinding senderBinding= sender.resolveTypeBinding();
		if (senderBinding == null || Modifier.isFinal(senderBinding.getModifiers())) {
			return;
		}

		if (sender instanceof Name name && name.resolveBinding() instanceof ITypeBinding) {
			return; // static access
		}

		ASTNode parent= invocationNode.getParent();
		while (parent instanceof Expression && parent.getNodeType() != ASTNode.CAST_EXPRESSION) {
			parent= parent.getParent();
		}
		boolean hasCastProposal= false;
		if (parent instanceof CastExpression castExpression) {
			//	(TestCase) x.getName() -> ((TestCase) x).getName
			hasCastProposal = useExistingParentCastProposal(cu, castExpression, sender, invocationNode.getName(), getArgumentTypes(invocationNode.arguments()), proposals);
		}
		if (!hasCastProposal) {
			// x.getName() -> ((TestCase) x).getName

			Expression target= sender;
			while (target instanceof ParenthesizedExpression parenthesizedExpression) {
				target = parenthesizedExpression.getExpression();
			}

			String label;
			if (target.getNodeType() != ASTNode.CAST_EXPRESSION) {
				String targetName= null;
				if (target.getLength() <= 18) {
					targetName= ASTNodes.asString(target);
				}
				if (targetName == null) {
					label= CorrectionMessages.UnresolvedElementsSubProcessor_methodtargetcast_description;
				} else {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_methodtargetcast2_description, BasicElementLabels.getJavaCodeString(targetName));
				}
			} else {
				String targetName= null;
				if (target.getLength() <= 18) {
					targetName= ASTNodes.asString(((CastExpression)target).getExpression());
				}
				if (targetName == null) {
					label= CorrectionMessages.UnresolvedElementsSubProcessor_changemethodtargetcast_description;
				} else {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_changemethodtargetcast2_description, BasicElementLabels.getJavaCodeString(targetName));
				}
			}
			CastCorrectionProposalCore proposal = new CastCorrectionProposalCore(label, cu, target, (ITypeBinding) null, IProposalRelevance.CHANGE_CAST);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}
	}

	private static boolean useExistingParentCastProposal(ICompilationUnit cu, CastExpression expression,
			Expression accessExpression, SimpleName accessSelector, ITypeBinding[] paramTypes,
			Collection<ProposalKindWrapper> proposals) {
		ITypeBinding castType= expression.getType().resolveBinding();
		if (castType == null) {
			return false;
		}
		if (paramTypes != null) {
			if (Bindings.findMethodInHierarchy(castType, accessSelector.getIdentifier(), paramTypes) == null) {
				return false;
			}
		} else if (Bindings.findFieldInHierarchy(castType, accessSelector.getIdentifier()) == null) {
			return false;
		}
		ITypeBinding bindingToCast= accessExpression.resolveTypeBinding();
		if (bindingToCast != null && !bindingToCast.isCastCompatible(castType)) {
			return false;
		}

		IMethodBinding res= Bindings.findMethodInHierarchy(castType, accessSelector.getIdentifier(), paramTypes);
		if (res != null) {
			AST ast= expression.getAST();
			ASTRewrite rewrite= ASTRewrite.create(ast);
			CastExpression newCast= ast.newCastExpression();
			newCast.setType((Type) ASTNode.copySubtree(ast, expression.getType()));
			newCast.setExpression((Expression) rewrite.createCopyTarget(accessExpression));
			ParenthesizedExpression parents= ast.newParenthesizedExpression();
			parents.setExpression(newCast);

			ASTNode node= rewrite.createCopyTarget(expression.getExpression());
			rewrite.replace(expression, node, null);
			rewrite.replace(accessExpression, parents, null);

			String label= CorrectionMessages.UnresolvedElementsSubProcessor_missingcastbrackets_description;
			ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, cu, rewrite,
					IProposalRelevance.ADD_PARENTHESES_AROUND_CAST);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			return true;
		}
		return false;
	}

	private static void addParameterMissmatchProposals(IInvocationContextCore context, IProblemLocationCore problem,
			List<IMethodBinding> similarElements, ASTNode invocationNode, List<Expression> arguments,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		int nSimilarElements= similarElements.size();
		ITypeBinding[] argTypes= getArgumentTypes(arguments);
		if (argTypes == null || nSimilarElements == 0)  {
			return;
		}

		for (int i= 0; i < nSimilarElements; i++) {
			IMethodBinding elem = similarElements.get(i);
			int diff= elem.getParameterTypes().length - argTypes.length;
			if (diff == 0) {
				int nProposals= proposals.size();
				doEqualNumberOfParameters(context, invocationNode, problem, arguments, argTypes, elem, proposals);
				if (nProposals != proposals.size()) {
					return; // only suggest for one method (avoid duplicated proposals)
				}
			} else if (diff > 0) {
				doMoreParameters(context, invocationNode, argTypes, elem, proposals);
			} else {
				doMoreArguments(context, invocationNode, arguments, argTypes, elem, proposals);
			}
		}
	}

	private static void doMoreParameters(IInvocationContextCore context, ASTNode invocationNode, ITypeBinding[] argTypes,
			IMethodBinding methodBinding, Collection<ProposalKindWrapper> proposals) throws CoreException {
		ITypeBinding[] paramTypes= methodBinding.getParameterTypes();
		int k= 0, nSkipped= 0;
		int diff= paramTypes.length - argTypes.length;
		int[] indexSkipped= new int[diff];
		for (int i= 0; i < paramTypes.length; i++) {
			if (k < argTypes.length && canAssign(argTypes[k], paramTypes[i])) {
				k++; // match
			} else {
				if (nSkipped >= diff) {
					return; // too different
				}
				indexSkipped[nSkipped++]= i;
			}
		}
		ITypeBinding declaringType= methodBinding.getDeclaringClass();
		ICompilationUnit cu= context.getCompilationUnit();
		CompilationUnit astRoot= context.getASTRoot();

		// add arguments
		{
			String[] arg = new String[] { ASTResolving.getMethodSignature(methodBinding) };
			String label;
			if (diff == 1) {
				label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_addargument_description, arg);
			} else {
				label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_addarguments_description, arg);
			}
			AddArgumentCorrectionProposalCore proposal = new AddArgumentCorrectionProposalCore(label, context.getCompilationUnit(), invocationNode, indexSkipped, paramTypes, IProposalRelevance.ADD_ARGUMENTS);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}

		// remove parameters
		if (!declaringType.isFromSource()) {
			return;
		}

		ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, declaringType);
		if (targetCU != null) {
			IMethodBinding methodDecl= methodBinding.getMethodDeclaration();
			ITypeBinding[] declParameterTypes= methodDecl.getParameterTypes();

			ChangeDescription[] changeDesc= new ChangeDescription[declParameterTypes.length];
			ITypeBinding[] changedTypes= new ITypeBinding[diff];
			for (int i= diff - 1; i >= 0; i--) {
				int idx= indexSkipped[i];
				changeDesc[idx]= new RemoveDescription();
				changedTypes[i]= declParameterTypes[idx];
			}
			String[] arg = new String[] { ASTResolving.getMethodSignature(methodDecl), getTypeNames(changedTypes) };
			String label;
			if (methodDecl.isConstructor()) {
				if (diff == 1) {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_removeparam_constr_description, arg);
				} else {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_removeparams_constr_description, arg);
				}
			} else {
				if (diff == 1) {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_removeparam_description, arg);
				} else {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_removeparams_description, arg);
				}
			}

			ChangeMethodSignatureProposalCore proposal = new ChangeMethodSignatureProposalCore(label, targetCU, invocationNode,
					methodDecl, changeDesc, null, IProposalRelevance.CHANGE_METHOD_REMOVE_PARAMETER);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}
	}

	private static String getTypeNames(ITypeBinding[] types) {
		StringBuffer buf= new StringBuffer();
		for (int i= 0; i < types.length; i++) {
			if (i > 0) {
				buf.append(", "); //$NON-NLS-1$
			}
			buf.append(ASTResolving.getTypeSignature(types[i]));
		}
		return BasicElementLabels.getJavaElementName(buf.toString());
	}

	private static String getArgumentName(List<Expression> arguments, int index) {
		String def= String.valueOf(index + 1);

		ASTNode expr= arguments.get(index);
		if (expr.getLength() > 18) {
			return def;
		}
		ASTMatcher matcher= new ASTMatcher();
		for (int i= 0; i < arguments.size(); i++) {
			if (i != index && matcher.safeSubtreeMatch(expr, arguments.get(i))) {
				return def;
			}
		}
		return '\'' + BasicElementLabels.getJavaElementName(ASTNodes.asString(expr)) + '\'';
	}

	private static void doMoreArguments(IInvocationContextCore context, ASTNode invocationNode, List<Expression> arguments,
			ITypeBinding[] argTypes, IMethodBinding methodRef, Collection<ProposalKindWrapper> proposals)
					throws CoreException {
		ITypeBinding[] paramTypes= methodRef.getParameterTypes();
		int k= 0, nSkipped= 0;
		int diff= argTypes.length - paramTypes.length;
		int[] indexSkipped= new int[diff];
		for (int i= 0; i < argTypes.length; i++) {
			if (k < paramTypes.length && canAssign(argTypes[i], paramTypes[k])) {
				k++; // match
			} else {
				if (nSkipped >= diff) {
					return; // too different
				}
				indexSkipped[nSkipped++]= i;
			}
		}

		ICompilationUnit cu= context.getCompilationUnit();
		CompilationUnit astRoot= context.getASTRoot();

		// remove arguments
		{
			ASTRewrite rewrite= ASTRewrite.create(astRoot.getAST());

			for (int i= diff - 1; i >= 0; i--) {
				rewrite.remove(arguments.get(indexSkipped[i]), null);
			}
			String[] arg = new String[] { ASTResolving.getMethodSignature(methodRef) };
			String label;
			if (diff == 1) {
				label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_removeargument_description, arg);
			} else {
				label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_removearguments_description, arg);
			}
			ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, cu, rewrite,
					IProposalRelevance.REMOVE_ARGUMENTS);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}

		IMethodBinding methodDecl= methodRef.getMethodDeclaration();
		ITypeBinding declaringType= methodDecl.getDeclaringClass();

		// add parameters
		if (!declaringType.isFromSource()) {
			return;
		}
		ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, declaringType);
		if (targetCU != null) {

			if (isImplicitConstructor(methodDecl)) {
				return;
			}

			ChangeMethodSignatureProposalCore.ChangeDescription[] changeDesc = new ChangeMethodSignatureProposalCore.ChangeDescription[argTypes.length];
			ITypeBinding[] changeTypes= new ITypeBinding[diff];
			for (int i= diff - 1; i >= 0; i--) {
				int idx= indexSkipped[i];
				Expression arg= arguments.get(idx);
				String name= getExpressionBaseName(arg);
				ITypeBinding newType= Bindings.normalizeTypeBinding(argTypes[idx]);
				if (newType == null) {
					newType= astRoot.getAST().resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
				}
				if (newType.isWildcardType()) {
					newType= ASTResolving.normalizeWildcardType(newType, true, astRoot.getAST());
				}
				if (!ASTResolving.isUseableTypeInContext(newType, methodDecl, false)) {
					return;
				}
				changeDesc[idx]= new InsertDescription(newType, name);
				changeTypes[i]= newType;
			}
			String[] arg = new String[] { ASTResolving.getMethodSignature(methodDecl), getTypeNames(changeTypes) };
			String label;
			if (methodDecl.isConstructor()) {
				if (diff == 1) {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_addparam_constr_description, arg);
				} else {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_addparams_constr_description, arg);
				}
			} else {
				if (diff == 1) {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_addparam_description, arg);
				} else {
					label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_addparams_description, arg);
				}
			}
			ChangeMethodSignatureProposalCore proposal = new ChangeMethodSignatureProposalCore(label, targetCU, invocationNode,
					methodDecl, changeDesc, null, IProposalRelevance.CHANGE_METHOD_ADD_PARAMETER);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}
	}

	private static boolean isImplicitConstructor(IMethodBinding meth) {
		return meth.isDefaultConstructor();
	}

	private static ITypeBinding[] getParameterTypes(List<Expression> args) {
		ITypeBinding[] params= new ITypeBinding[args.size()];
		for (int i= 0; i < args.size(); i++) {
			Expression expr= args.get(i);
			ITypeBinding curr= Bindings.normalizeTypeBinding(expr.resolveTypeBinding());
			if (curr != null && curr.isWildcardType()) {
				curr= ASTResolving.normalizeWildcardType(curr, true, expr.getAST());
			}
			if (curr == null) {
				curr= expr.getAST().resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
			}
			params[i]= curr;
		}
		return params;
	}

	private static void doEqualNumberOfParameters(IInvocationContextCore context, ASTNode invocationNode,
			IProblemLocationCore problem, List<Expression> arguments, ITypeBinding[] argTypes, IMethodBinding methodBinding,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		ITypeBinding[] paramTypes= methodBinding.getParameterTypes();
		int[] indexOfDiff= new int[paramTypes.length];
		int nDiffs= 0;
		for (int n= 0; n < argTypes.length; n++) {
			if (!canAssign(argTypes[n], paramTypes[n])) {
				indexOfDiff[nDiffs++]= n;
			}
		}
		ITypeBinding declaringTypeDecl= methodBinding.getDeclaringClass().getTypeDeclaration();

		ICompilationUnit cu= context.getCompilationUnit();
		CompilationUnit astRoot= context.getASTRoot();

		ASTNode nameNode= problem.getCoveringNode(astRoot);
		if (nameNode == null) {
			return;
		}

		if (nDiffs == 0) {
			if (nameNode.getParent() instanceof MethodInvocation inv) {
				if (inv.getExpression() == null) {
					addQualifierToOuterProposal(context, inv, methodBinding, proposals);
				}
			}
			return;
		}

		if (nDiffs == 1) { // one argument mismatching: try to fix
			int idx= indexOfDiff[0];
			Expression nodeToCast= arguments.get(idx);
			ITypeBinding castType= paramTypes[idx];
			castType= Bindings.normalizeTypeBinding(castType);
			if (castType.isWildcardType()) {
				castType= ASTResolving.normalizeWildcardType(castType, false, nodeToCast.getAST());
			}
			if (castType != null) {
				ITypeBinding binding= nodeToCast.resolveTypeBinding();
				ITypeBinding castFixType= null;
				if (binding == null || castType.isCastCompatible(binding)) {
					castFixType= castType;
				} else if (JavaModelUtil.is50OrHigher(cu.getJavaProject())) {
					ITypeBinding boxUnboxedTypeBinding= TypeMismatchSubProcessor.boxUnboxPrimitives(castType, binding, nodeToCast.getAST());
					if (boxUnboxedTypeBinding != castType && boxUnboxedTypeBinding.isCastCompatible(binding)) {
						castFixType= boxUnboxedTypeBinding;
					}
				}
				if (castFixType != null) {
					ProposalKindWrapper proposal = TypeMismatchSubProcessor.createCastProposal(context, castFixType, nodeToCast, IProposalRelevance.CAST_ARGUMENT_1);
					String castTypeName= BindingLabelProviderCore.getBindingLabel(castFixType, JavaElementLabelsCore.ALL_DEFAULT);
					String[] arg= new String[] { getArgumentName(arguments, idx), castTypeName};
					proposal.getProposal().setDisplayName(Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_addargumentcast_description, arg));
					proposals.add(proposal);
				}

				TypeMismatchSubProcessor.addChangeSenderTypeProposals(context, nodeToCast, castType, false, IProposalRelevance.CAST_ARGUMENT_2, proposals);
			}
		}

		if (nDiffs == 2) { // try to swap
			int idx1= indexOfDiff[0];
			int idx2= indexOfDiff[1];
			boolean canSwap= canAssign(argTypes[idx1], paramTypes[idx2]) && canAssign(argTypes[idx2], paramTypes[idx1]);
			if (canSwap) {
				Expression arg1= arguments.get(idx1);
				Expression arg2= arguments.get(idx2);

				ASTRewrite rewrite= ASTRewrite.create(astRoot.getAST());
				rewrite.replace(arg1, rewrite.createCopyTarget(arg2), null);
				rewrite.replace(arg2, rewrite.createCopyTarget(arg1), null);
				{
					String[] arg= new String[] { getArgumentName(arguments, idx1), getArgumentName(arguments, idx2) };
					String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_swaparguments_description, arg);
					ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label,
							context.getCompilationUnit(), rewrite, IProposalRelevance.SWAP_ARGUMENTS);
					proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
				}

				if (declaringTypeDecl.isFromSource()) {
					ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, declaringTypeDecl);
					if (targetCU != null) {
						ChangeDescription[] changeDesc= new ChangeDescription[paramTypes.length];
						for (int i= 0; i < nDiffs; i++) {
							changeDesc[idx1]= new SwapDescription(idx2);
						}
						IMethodBinding methodDecl= methodBinding.getMethodDeclaration();
						ITypeBinding[] declParamTypes= methodDecl.getParameterTypes();

						ITypeBinding[] swappedTypes= new ITypeBinding[] { declParamTypes[idx1], declParamTypes[idx2] };
						String[] args = new String[] { ASTResolving.getMethodSignature(methodDecl),
								getTypeNames(swappedTypes) };
						String label;
						if (methodDecl.isConstructor()) {
							label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_swapparams_constr_description, args);
						} else {
							label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_swapparams_description, args);
						}
						ChangeMethodSignatureProposalCore proposal = new ChangeMethodSignatureProposalCore(label, targetCU,
								invocationNode, methodDecl, changeDesc, null,
								IProposalRelevance.CHANGE_METHOD_SWAP_PARAMETERS);
						proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
					}
				}
				return;
			}
		}

		if (declaringTypeDecl.isFromSource()) {
			ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, declaringTypeDecl);
			if (targetCU != null) {
				ChangeDescription[] changeDesc= createSignatureChangeDescription(indexOfDiff, nDiffs, paramTypes, arguments, argTypes);
				if (changeDesc != null) {

					IMethodBinding methodDecl= methodBinding.getMethodDeclaration();
					ITypeBinding[] declParamTypes= methodDecl.getParameterTypes();

					ITypeBinding[] newParamTypes= new ITypeBinding[changeDesc.length];
					for (int i= 0; i < newParamTypes.length; i++) {
						newParamTypes[i]= changeDesc[i] == null ? declParamTypes[i] : ((EditDescription) changeDesc[i]).type;
					}
					boolean isVarArgs= methodDecl.isVarargs() && newParamTypes.length > 0 && newParamTypes[newParamTypes.length - 1].isArray();
					String[] args = new String[] { ASTResolving.getMethodSignature(methodDecl), ASTResolving.getMethodSignature(methodDecl.getName(), newParamTypes, isVarArgs) };
					String label;
					if (methodDecl.isConstructor()) {
						label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_changeparamsignature_constr_description, args);
					} else {
						label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_changeparamsignature_description, args);
					}
					ChangeMethodSignatureProposalCore proposal = new ChangeMethodSignatureProposalCore(label, targetCU,
							invocationNode, methodDecl, changeDesc, null, IProposalRelevance.CHANGE_METHOD_SIGNATURE);
					proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
				}
			}
		}
	}

	private static ChangeDescription[] createSignatureChangeDescription(int[] indexOfDiff, int nDiffs, ITypeBinding[] paramTypes, List<Expression> arguments, ITypeBinding[] argTypes) {
		ChangeDescription[] changeDesc= new ChangeDescription[paramTypes.length];
		for (int i= 0; i < nDiffs; i++) {
			int diffIndex= indexOfDiff[i];
			Expression arg= arguments.get(diffIndex);
			String name= getExpressionBaseName(arg);
			ITypeBinding argType= argTypes[diffIndex];
			if (argType.isWildcardType()) {
				argType= ASTResolving.normalizeWildcardType(argType, true, arg.getAST());
				if (argType== null) {
					return null;
				}
			}
			changeDesc[diffIndex]= new EditDescription(argType, name);
		}
		return changeDesc;
	}

	private static String getExpressionBaseName(Expression expr) {
		IBinding argBinding= Bindings.resolveExpressionBinding(expr, true);
		if (argBinding instanceof IVariableBinding argVariableBinding) {
			IJavaProject project= null;
			ASTNode root= expr.getRoot();
			if (root instanceof CompilationUnit unit) {
				ITypeRoot typeRoot = unit.getTypeRoot();
				if (typeRoot != null) {
					project= typeRoot.getJavaProject();
				}
			}
			return StubUtility.getBaseName(argVariableBinding, project);
		}
		if (expr instanceof SimpleName name) {
			return name.getIdentifier();
		}
		return null;
	}

	private static ITypeBinding[] getArgumentTypes(List<Expression> arguments) {
		ITypeBinding[] res= new ITypeBinding[arguments.size()];
		for (int i= 0; i < res.length; i++) {
			Expression expression= arguments.get(i);
			ITypeBinding curr= expression.resolveTypeBinding();
			if (curr == null) {
				return null;
			}
			if (!curr.isNullType()) {	// don't normalize null type
				curr= Bindings.normalizeTypeBinding(curr);
				if (curr == null) {
					curr= expression.getAST().resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
				}
			}
			res[i]= curr;
		}
		return res;
	}

	private static void addQualifierToOuterProposal(IInvocationContextCore context, MethodInvocation invocationNode,
			IMethodBinding binding, Collection<ProposalKindWrapper> proposals) {
		ITypeBinding declaringType= binding.getDeclaringClass();
		ITypeBinding parentType= Bindings.getBindingOfParentType(invocationNode);
		ITypeBinding currType= parentType;

		boolean isInstanceMethod= !Modifier.isStatic(binding.getModifiers());

		while (currType != null && !Bindings.isSuperType(declaringType, currType)) {
			if (isInstanceMethod && Modifier.isStatic(currType.getModifiers())) {
				return;
			}
			currType= currType.getDeclaringClass();
		}
		if (currType == null || currType == parentType) {
			return;
		}

		ASTRewrite rewrite= ASTRewrite.create(invocationNode.getAST());

		String label = Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_changetoouter_description,
				ASTResolving.getTypeSignature(currType));
		ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, context.getCompilationUnit(),
				rewrite, IProposalRelevance.QUALIFY_WITH_ENCLOSING_TYPE);

		ImportRewrite imports= proposal.createImportRewrite(context.getASTRoot());
		ImportRewriteContext importRewriteContext= new ContextSensitiveImportRewriteContext(invocationNode, imports);
		AST ast= invocationNode.getAST();

		String qualifier= imports.addImport(currType, importRewriteContext);
		Name name= ASTNodeFactory.newName(ast, qualifier);

		Expression newExpression;
		if (isInstanceMethod) {
			ThisExpression expr= ast.newThisExpression();
			expr.setQualifier(name);
			newExpression= expr;
		} else {
			newExpression= name;
		}

		rewrite.set(invocationNode, MethodInvocation.EXPRESSION_PROPERTY, newExpression, null);
		proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
	}


	public static void getConstructorProposals(IInvocationContextCore context, IProblemLocationCore problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		ICompilationUnit cu= context.getCompilationUnit();

		CompilationUnit astRoot= context.getASTRoot();
		ASTNode selectedNode= problem.getCoveringNode(astRoot);
		if (selectedNode == null) {
			return;
		}

		ITypeBinding targetBinding= null;
		List<Expression> arguments= null;
		IMethodBinding recursiveConstructor= null;

		int type= selectedNode.getNodeType();
		if (type == ASTNode.CLASS_INSTANCE_CREATION) {
			ClassInstanceCreation creation= (ClassInstanceCreation) selectedNode;

			IBinding binding= creation.getType().resolveBinding();
			if (binding instanceof ITypeBinding typeBinding) {
				targetBinding = typeBinding;
				arguments= creation.arguments();
			}
		} else if (type == ASTNode.SUPER_CONSTRUCTOR_INVOCATION) {
			ITypeBinding typeBinding= Bindings.getBindingOfParentType(selectedNode);
			if (typeBinding != null && !typeBinding.isAnonymous()) {
				targetBinding= typeBinding.getSuperclass();
				arguments= ((SuperConstructorInvocation) selectedNode).arguments();
			}
		} else if (type == ASTNode.CONSTRUCTOR_INVOCATION) {
			ITypeBinding typeBinding= Bindings.getBindingOfParentType(selectedNode);
			if (typeBinding != null && !typeBinding.isAnonymous()) {
				targetBinding= typeBinding;
				arguments= ((ConstructorInvocation) selectedNode).arguments();
				recursiveConstructor= ASTResolving.findParentMethodDeclaration(selectedNode).resolveBinding();
			}
		}
		if (targetBinding == null) {
			return;
		}
		IMethodBinding[] methods= targetBinding.getDeclaredMethods();
		ArrayList<IMethodBinding> similarElements= new ArrayList<>();
		for (int i= 0; i < methods.length; i++) {
			IMethodBinding curr= methods[i];
			if (curr.isConstructor() && recursiveConstructor != curr) {
				similarElements.add(curr); // similar elements can contain a implicit default constructor
			}
		}

		addParameterMissmatchProposals(context, problem, similarElements, selectedNode, arguments, proposals);

		if (targetBinding.isFromSource()) {
			ITypeBinding targetDecl= targetBinding.getTypeDeclaration();

			ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, targetDecl);
			if (targetCU != null) {
				String[] args = new String[] {
						ASTResolving.getMethodSignature(ASTResolving.getTypeSignature(targetDecl), getParameterTypes(arguments), false) };
				String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_createconstructor_description, args);
				NewMethodCorrectionProposalCore proposal = new NewMethodCorrectionProposalCore(label, targetCU, selectedNode, arguments, targetDecl, IProposalRelevance.CREATE_CONSTRUCTOR);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}
		}
	}

	public static void getAmbiguousTypeReferenceProposals(IInvocationContextCore context, IProblemLocationCore problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		final ICompilationUnit cu= context.getCompilationUnit();
		int offset= problem.getOffset();
		int len= problem.getLength();

		IJavaElement[] elements= cu.codeSelect(offset, len);
		for (int i= 0; i < elements.length; i++) {
			IJavaElement curr= elements[i];
			if (curr instanceof IType type && !TypeFilter.isFiltered(type)) {
				String qualifiedTypeName = type.getFullyQualifiedName('.');

				CompilationUnit root= context.getASTRoot();

				String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_importexplicit_description, BasicElementLabels.getJavaElementName(qualifiedTypeName));
				ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, cu, ASTRewrite.create(root.getAST()), IProposalRelevance.IMPORT_EXPLICIT);

				ImportRewrite imports= proposal.createImportRewrite(root);
				imports.addImport(qualifiedTypeName);

				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}
		}
	}

	public static void getArrayAccessProposals(IInvocationContextCore context, IProblemLocationCore problem,
			Collection<ProposalKindWrapper> proposals) {

		CompilationUnit root= context.getASTRoot();
		ASTNode selectedNode= problem.getCoveringNode(root);
		if (!(selectedNode instanceof MethodInvocation)) {
			return;
		}

		MethodInvocation decl= (MethodInvocation) selectedNode;
		SimpleName nameNode= decl.getName();
		String methodName= nameNode.getIdentifier();

		IBinding[] bindings= (new ScopeAnalyzer(root)).getDeclarationsInScope(nameNode, ScopeAnalyzer.METHODS);
		for (int i= 0; i < bindings.length; i++) {
			String currName= bindings[i].getName();
			if (NameMatcher.isSimilarName(methodName, currName)) {
				String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_arraychangetomethod_description, BasicElementLabels.getJavaElementName(currName));
				RenameNodeCorrectionProposalCore proposal = new RenameNodeCorrectionProposalCore(label, context.getCompilationUnit(), nameNode.getStartPosition(), nameNode.getLength(), currName, IProposalRelevance.ARRAY_CHANGE_TO_METHOD);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}
		}
		// always suggest 'length'
		String lengthId= "length"; //$NON-NLS-1$
		String label= CorrectionMessages.UnresolvedElementsSubProcessor_arraychangetolength_description;
		int offset= nameNode.getStartPosition();
		int length= decl.getStartPosition() + decl.getLength() - offset;
		RenameNodeCorrectionProposalCore proposal = new RenameNodeCorrectionProposalCore(label, context.getCompilationUnit(), offset, length, lengthId, IProposalRelevance.ARRAY_CHANGE_TO_LENGTH);
		proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
	}

	public static void getAnnotationMemberProposals(IInvocationContextCore context, IProblemLocationCore problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		CompilationUnit astRoot= context.getASTRoot();
		ICompilationUnit cu= context.getCompilationUnit();
		ASTNode selectedNode= problem.getCoveringNode(astRoot);

		Annotation annotation;
		String memberName;
		if (selectedNode.getLocationInParent() == MemberValuePair.NAME_PROPERTY) {
			if (selectedNode.getParent().getLocationInParent() != NormalAnnotation.VALUES_PROPERTY) {
				return;
			}
			annotation= (Annotation) selectedNode.getParent().getParent();
			memberName= ((SimpleName) selectedNode).getIdentifier();
		} else if (selectedNode.getLocationInParent() == SingleMemberAnnotation.VALUE_PROPERTY) {
			annotation= (Annotation) selectedNode.getParent();
			memberName= "value"; //$NON-NLS-1$
		} else {
			return;
		}

		ITypeBinding annotBinding= annotation.resolveTypeBinding();
		if (annotBinding == null) {
			return;
		}


		if (annotation instanceof NormalAnnotation) {
			// similar names
			IMethodBinding[] otherMembers= annotBinding.getDeclaredMethods();
			for (int i= 0; i < otherMembers.length; i++) {
				IMethodBinding binding= otherMembers[i];
				String curr= binding.getName();
				int relevance= NameMatcher.isSimilarName(memberName, curr) ? IProposalRelevance.CHANGE_TO_ATTRIBUTE_SIMILAR_NAME : IProposalRelevance.CHANGE_TO_ATTRIBUTE;
				String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_UnresolvedElementsSubProcessor_changetoattribute_description, BasicElementLabels.getJavaElementName(curr));
				RenameNodeCorrectionProposalCore proposal = new RenameNodeCorrectionProposalCore(label, cu, problem.getOffset(), problem.getLength(), curr, relevance);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}
		}

		if (annotBinding.isFromSource()) {
			ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, annotBinding);
			if (targetCU != null) {
				String label= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_UnresolvedElementsSubProcessor_createattribute_description, BasicElementLabels.getJavaElementName(memberName));
				NewAnnotationMemberProposalCore proposal = new NewAnnotationMemberProposalCore(label, targetCU, selectedNode, annotBinding, IProposalRelevance.CREATE_ATTRIBUTE);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}
		}
	}


}
