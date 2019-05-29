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
package io.zeebe.logstreams.state;

import io.zeebe.db.ZeebeDb;
import io.zeebe.logstreams.spi.SnapshotController;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public class NoopSnapshotController implements SnapshotController {

  @Override
  public void takeSnapshot(final long lowerBoundSnapshotPosition) {}

  @Override
  public void close() throws Exception {}

  @Override
  public void takeTempSnapshot() {}

  @Override
  public void moveValidSnapshot(final long lowerBoundSnapshotPosition) throws IOException {}

  @Override
  public void replicateLatestSnapshot(final Consumer<Runnable> executor) {}

  @Override
  public void consumeReplicatedSnapshots() {}

  @Override
  public long recover() throws Exception {
    return 0;
  }

  @Override
  public ZeebeDb openDb() {
    return null;
  }

  @Override
  public long getPositionToDelete(final int maxSnapshotCount) {
    return 0;
  }

  @Override
  public int getValidSnapshotsCount() {
    return 0;
  }

  @Override
  public long getLastValidSnapshotPosition() {
    return 0;
  }

  @Override
  public File getLastValidSnapshotDirectory() {
    return null;
  }

  @Override
  public File getSnapshotDirectoryFor(long snapshotId) {
    return null;
  }
}
