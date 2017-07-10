/**
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
package io.zeebe.logstreams.spi;

/**
 * Storage for log snapshots.
 */
public interface SnapshotStorage
{

    /**
     * Returns the last snapshot for the given name.
     *
     * @param name
     *            the name of the snapshot
     * @return the snapshot or <code>null</code> if none exists
     * @throws Exception
     *             if fails to open the snapshot
     */
    ReadableSnapshot getLastSnapshot(String name) throws Exception;

    /**
     * Returns a writer to create a new snapshot.
     *
     * @param name
     *            the name of the snapshot
     * @param logPosition
     *            the log position at which the snapshot is taken
     * @return the writer to create the snapshot
     * @throws Exception
     *             if fails to create the snapshot
     */
    SnapshotWriter createSnapshot(String name, long logPosition) throws Exception;

    /**
     * Deletes all existing snapshot and checksum files.
     *
     * @param name the name of the snapshot
     * @return true if the purging was successful otherwise false
     */
    boolean purgeSnapshot(String name);

}
