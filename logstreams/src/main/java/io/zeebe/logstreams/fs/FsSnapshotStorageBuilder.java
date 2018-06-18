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
package io.zeebe.logstreams.fs;

import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorage;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorageConfiguration;
import io.zeebe.logstreams.spi.SnapshotStorage;
import java.io.File;
import java.util.Objects;

public class FsSnapshotStorageBuilder {
  protected String rootPath;

  public FsSnapshotStorageBuilder(String rootPath) {
    this.rootPath = rootPath;
  }

  public FsSnapshotStorageBuilder rootPath(String rootPath) {
    this.rootPath = rootPath;
    return this;
  }

  public SnapshotStorage build() {
    Objects.requireNonNull(rootPath, "rootPath cannot be null");

    final File file = new File(rootPath);

    if (!file.exists()) {
      file.mkdirs();
    }

    final FsSnapshotStorageConfiguration cfg = new FsSnapshotStorageConfiguration();
    cfg.setRootPath(rootPath);

    return new FsSnapshotStorage(cfg);
  }
}
