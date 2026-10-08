/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.RelativePattern;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

/**
 * Exercises the actual {@link StandardProjectsManager#registerWatchers()} production code against
 * a real imported Maven project ("maven/salut"), placed under a working directory whose path
 * contains a directory literally named {@code src} as an ancestor of the project root -- the same
 * physical shape reported in https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3911 (e.g.
 * {@code /home/user/src/work/demo}). "salut" itself uses the standard Maven layout, i.e.
 * {@code src/} directly under the project root, produced by {@code mvn archetype:generate}.
 *
 * <p>{@link #getWorkingProjectDirectory()} is overridden so that every project imported by a test
 * in this class lands under a path containing that {@code src} ancestor, without needing to fake
 * or hand-construct any paths or patterns -- the project's real {@link IProject#getLocation()} is
 * genuinely under such a path, and the patterns under test come straight out of
 * {@link StandardProjectsManager#registerWatchers()}.</p>
 */
public class StandardProjectsManagerSourceWatcherTest extends AbstractProjectsManagerBasedTest {

	@Override
	protected File getWorkingProjectDirectory() throws IOException {
		// Nested inside the default working directory (rather than a separate location) so that
		// AbstractProjectsManagerBasedTest#cleanUp() deletes it for free, in the order it already
		// knows is safe (workspace projects deleted before their backing directories).
		File dir = new File(super.getWorkingProjectDirectory(), "src/work");
		FileUtils.forceMkdir(dir);
		return dir;
	}

	private static boolean matches(String globPattern, String absolutePath) {
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
		return matcher.matches(Path.of(absolutePath));
	}

	private static String patternString(FileSystemWatcher watcher) {
		return watcher.getGlobPattern().map(Function.identity(), RelativePattern::getPattern);
	}

	/**
	 * Imports "maven/salut" (landing under a {@code src}-named ancestor directory per
	 * {@link #getWorkingProjectDirectory()}), registers watchers, and returns the single
	 * project-scoped src pattern {@code registerWatchers()} generated for it.
	 */
	private String importAndGetProjectScopedSrcPattern() throws Exception {
		ClientPreferences mockCapabilities = mock(ClientPreferences.class);
		when(mockCapabilities.isWorkspaceChangeWatchedFilesDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilities);

		importProjects(Arrays.asList("maven/salut"));
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("salut");
		assertNotNull(project.getLocation(), "Test project should have a resolvable location");
		assertTrue(project.getLocation().toPortableString().contains("/src/"), "Test setup sanity check: project should be located under a 'src' ancestor directory, was: " + project.getLocation());

		List<FileSystemWatcher> watchers = projectsManager.registerWatchers();

		// Find the project-scoped src pattern PR #3918 generates for this project: a plain
		// (Either.forLeft) pattern containing the project's own absolute location, as opposed to
		// the unrelated "**/*.java", "**/.project", build-tool, etc. patterns also present.
		String locationPortable = project.getLocation().toPortableString();
		List<FileSystemWatcher> candidateSrcWatchers = watchers.stream().filter(w -> w.getGlobPattern().isLeft()).filter(w -> patternString(w).contains(locationPortable)).filter(w -> patternString(w).endsWith("/src/**")).toList();
		assertTrue(candidateSrcWatchers.size() >= 1, "Expected a project-scoped src watcher for 'salut' among:\n" + watchers.stream().map(StandardProjectsManagerSourceWatcherTest::patternString).reduce("", (a, b) -> a + "\n" + b));
		return patternString(candidateSrcWatchers.get(0));
	}

	@Test
	public void testRegisteredSrcPatternDoesNotMatchBuildOutputUnderSrcNamedAncestor() throws Exception {
		// This is the part of PR #3918's fix that does work, verified through the real
		// production method, under the exact physical scenario reported in issue #3911: the
		// project lives under a directory literally named "src", and its own build output no
		// longer gets caught by the src watcher just because of that ancestor's name.
		String pattern = importAndGetProjectScopedSrcPattern();
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("salut");
		IPath location = project.getLocation();

		String buildOutputClassFile = location.append("target/classes/java/Foo.class").toOSString();
		assertFalse(matches(pattern, buildOutputClassFile), "registerWatchers() produced a src pattern (" + pattern + ") that (incorrectly) matched the project's own build output (" + buildOutputClassFile + "), "
				+ "even though the project is scoped. This would reproduce issue #3911.");
	}

	@Test
	public void testRegisteredSrcPatternMatchesProjectsOwnSourceFile() throws Exception {
		// "salut" uses the standard Maven layout: src/ directly under the project root. The
		// watcher registered for a project MUST match that project's own real source files --
		// that is the entire point of registering a src watcher. This assertion documents the
		// correct, expected behavior, and is currently expected to FAIL against PR #3918 as
		// written: the pattern it generates has zero path segments between the project root and
		// "src", which per glob semantics never matches ("a/**/b" requires at least one segment
		// between "a" and "b"). This test should go green once the fix correctly scopes the
		// pattern, e.g. via a RelativePattern with the project location as baseUri instead of a
		// hand-built plain glob string.
		String pattern = importAndGetProjectScopedSrcPattern();
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("salut");
		IPath location = project.getLocation();

		String realSourceFile = location.append("src/main/java/java/Foo.java").toOSString();
		assertTrue(matches(pattern, realSourceFile), "registerWatchers() produced a src pattern (" + pattern + ") that fails to match the project's own real source file (" + realSourceFile + "). "
				+ "This means jdtls stops watching source file changes for any standard single-module project (src/ directly under the project root).");
	}
}
