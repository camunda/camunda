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
package io.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileUtilTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void shouldDeleteFolder() throws IOException {
    final File root = tempFolder.getRoot();

    tempFolder.newFile("file1");
    tempFolder.newFile("file2");
    tempFolder.newFolder("testFolder");

    FileUtil.deleteFolder(root.getAbsolutePath());

    assertThat(root.exists()).isFalse();
  }

  @Test
  public void shouldThrowExceptionForNonExistingFolder() {
    final File root = tempFolder.getRoot();

    tempFolder.delete();

    assertThatThrownBy(
            () -> {
              FileUtil.deleteFolder(root.getAbsolutePath());
            })
        .isInstanceOf(NoSuchFileException.class);
  }

  @Test
  public void shouldCreateParentDirectory() {
    // given
    final String folderToCreate = "folder";
    final String fileToCreate = "file";
    final String filePathToCreate =
        tempFolder.getRoot().getPath()
            + File.separator
            + folderToCreate
            + File.separator
            + fileToCreate;

    // when
    FileUtil.openChannel(filePathToCreate, true);

    // then
    final File[] matchingFolders =
        tempFolder.getRoot().listFiles((dir, name) -> folderToCreate.equals(name));
    assertThat(matchingFolders).hasSize(1);

    final File folder = matchingFolders[0];
    final File[] filesInFolder = folder.listFiles();

    assertThat(filesInFolder).hasSize(1);
    assertThat(filesInFolder[0]).hasName(fileToCreate);
  }
}
