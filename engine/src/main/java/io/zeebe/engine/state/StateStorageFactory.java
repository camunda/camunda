/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.state;

import io.zeebe.engine.processor.StreamProcessorContext;
import io.zeebe.logstreams.state.StateStorage;
import java.io.File;

/**
 * This class may eventually be superseded by a more accurate StateStorage class local to the broker
 * core module if it ever needs more functionality than strictly creating stream processor specific
 * storage classes (e.g. listing all of them for regular maintenance jobs). If you find yourself
 * adding such functionality consider refactoring the whole thing.
 */
public class StateStorageFactory {
  public static final String DEFAULT_RUNTIME_PATH = "runtime";
  public static final String DEFAULT_SNAPSHOTS_PATH = "snapshots";

  private final File rootDirectory;

  public StateStorageFactory(final File rootDirectory) {
    this.rootDirectory = rootDirectory;
  }

  public StateStorage create(final int processorId, final String processorName) {
    final String name = String.format("%d_%s", processorId, processorName);
    final File processorDirectory = new File(rootDirectory, name);

    final File runtimeDirectory = new File(processorDirectory, DEFAULT_RUNTIME_PATH);
    final File snapshotsDirectory = new File(processorDirectory, DEFAULT_SNAPSHOTS_PATH);

    if (!processorDirectory.exists()) {
      processorDirectory.mkdir();
    }

    if (!snapshotsDirectory.exists()) {
      snapshotsDirectory.mkdir();
    }

    return new StateStorage(runtimeDirectory, snapshotsDirectory);
  }

  public StateStorage create(final StreamProcessorContext context) {
    return create(context.getId(), context.getName());
  }
}
