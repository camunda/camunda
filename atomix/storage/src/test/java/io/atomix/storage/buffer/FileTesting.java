/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.storage.buffer;

import static io.zeebe.test.util.TestEnvironment.getTestForkNumber;
import static io.zeebe.test.util.TestEnvironment.getTestMavenId;
import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

public abstract class FileTesting {

  private static final String getTestRunPathElement() {
    return format("fork-%d_mavenID-%d/", getTestForkNumber(), getTestMavenId());
  }

  private static final String getFolderNameForTestData() {
    return "target/test-files/" + getTestRunPathElement();
  }

  public static File createFile() {
    final File file = new File(getFolderNameForTestData() + UUID.randomUUID().toString());
    file.getParentFile().mkdirs();
    return file;
  }

  public static void cleanFiles() {
    final Path directory = Paths.get(getFolderNameForTestData());
    try {
      Files.walkFileTree(
          directory,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
                throws IOException {
              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (final Exception ignore) {
      // should not happen
    }
  }
}
