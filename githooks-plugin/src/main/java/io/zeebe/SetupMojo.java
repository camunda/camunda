/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "setup", defaultPhase = LifecyclePhase.INITIALIZE)
public class SetupMojo extends AbstractMojo {

  private static final Charset CHARSET = StandardCharsets.UTF_8;
  private static final String LINE_SEPARATOR = System.lineSeparator();

  private static final String SHEBANG = "#!/bin/sh" + LINE_SEPARATOR;
  private static final String PRE_COMMIT_SCRIPT = ".githooks/pre-commit" + LINE_SEPARATOR;

  @Parameter(
      defaultValue = "${maven.multiModuleProjectDirectory}",
      property = "rootDir",
      required = true)
  private File rootDir;

  public void execute() throws MojoExecutionException {
    ensureGitHooksDirExists();
    ensurePreCommitFileExists();
    ensurePreCommitFilePermissions();
    ensurePreCommitScript();
  }

  private void ensureGitHooksDirExists() throws MojoExecutionException {
    final Path gitHooksDir = getGitHooksDir();
    final File file = gitHooksDir.toFile();
    if (file.isDirectory()) {
      getLog().debug("Git hooks directory " + file.getAbsolutePath() + " exist");
    } else {
      if (file.exists()) {
        throw new MojoExecutionException(
            "Git hooks directory " + file.getAbsolutePath() + " exists but is not directory");
      } else {
        if (file.mkdirs()) {
          getLog().info("Created git hooks directory " + file.getAbsolutePath());
        } else {
          throw new MojoExecutionException(
              "Failed to create git hooks directory " + file.getAbsolutePath());
        }
      }
    }
  }

  private void ensurePreCommitFileExists() throws MojoExecutionException {
    final Path preCommitFile = getPreCommitFile();
    final File file = preCommitFile.toFile();
    if (file.isFile()) {
      getLog().debug("Pre-commit hook " + file.getAbsolutePath() + " file exist");
    } else {
      if (file.exists()) {
        throw new MojoExecutionException(
            "Pre-commit hook file " + file.getAbsolutePath() + " exists but is not a file");
      } else {
        try {
          Files.write(preCommitFile, SHEBANG.getBytes(CHARSET), CREATE);
          getLog().info("Created pre-commit hook file " + file.getAbsolutePath());
        } catch (final IOException e) {
          throw new MojoExecutionException(
              "Failed to create pre-commit hook file " + file.getAbsolutePath(), e);
        }
      }
    }
  }

  private void ensurePreCommitFilePermissions() throws MojoExecutionException {
    final Path preCommitFile = getPreCommitFile();
    try {
      final Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxrwxrwx");
      if (!Files.getPosixFilePermissions(preCommitFile).equals(permissions)) {
        Files.setPosixFilePermissions(preCommitFile, permissions);
        getLog()
            .info(
                "Set file permissions for pre-commit hook file " + preCommitFile.toAbsolutePath());
      }
    } catch (final UnsupportedOperationException e) {
      getLog().debug("Skip setting file permissions as it is not supported on this system", e);
    } catch (final IOException e) {
      throw new MojoExecutionException(
          "Failed to set file permissions for pre-commit hook file "
              + preCommitFile.toAbsolutePath(),
          e);
    }
  }

  private void ensurePreCommitScript() throws MojoExecutionException {
    final Path preCommitFile = getPreCommitFile();
    try {
      final String preCommitFileContent = new String(Files.readAllBytes(preCommitFile), CHARSET);
      if (!preCommitFileContent.contains(PRE_COMMIT_SCRIPT)) {
        Files.write(preCommitFile, PRE_COMMIT_SCRIPT.getBytes(CHARSET), APPEND);
        getLog().info("Added pre-commit script to file " + preCommitFile.toAbsolutePath());
      }
    } catch (final IOException e) {
      throw new MojoExecutionException(
          "Failed to read content of pre-commit file " + preCommitFile.toAbsolutePath());
    }
  }

  private Path getGitHooksDir() {
    return Paths.get(rootDir.getAbsolutePath(), ".git", "hooks");
  }

  private Path getPreCommitFile() {
    return getGitHooksDir().resolve("pre-commit");
  }
}
