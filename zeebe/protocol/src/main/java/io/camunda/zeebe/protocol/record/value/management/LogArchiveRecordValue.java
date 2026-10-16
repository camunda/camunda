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
package io.camunda.zeebe.protocol.record.value.management;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableLogArchiveRecordValue.Builder.class)
public interface LogArchiveRecordValue extends RecordValue {

  /**
   * @return the id of the timeline this record belongs to
   */
  String getTimelineId();

  /**
   * @return the commit sequence number assigned by the processor, or -1 if not assigned
   */
  long getSeq();

  /**
   * @return the first log index of the timeline, or -1 if not applicable
   */
  long getStartIndex();

  /**
   * @return the id of the parent timeline, or an empty string if there is none
   */
  String getParentTimelineId();

  /**
   * @return the uploaded log objects, or an empty list
   */
  List<LogObjectValue> getObjects();

  /**
   * @return the uploaded snapshot, with default values if the record carries none
   */
  ArchivedSnapshotValue getSnapshot();

  /**
   * @return the sealed index chunk, with default values if the record carries none
   */
  IndexChunkValue getChunk();

  /**
   * @return the published sequence number, or -1 if not applicable
   */
  long getPublishedSeq();

  /**
   * @return the archived index after applying the event, or -1 if not set
   */
  long getArchivedIndex();

  /**
   * @return the archived position after applying the event, or -1 if not set
   */
  long getArchivedPosition();

  /**
   * @return the checkpoints covered by the archived log objects, or an empty list
   */
  List<CoveredCheckpointValue> getCheckpoints();

  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableLogObjectValue.Builder.class)
  interface LogObjectValue {

    /**
     * @return the object key relative to the partition prefix
     */
    String getKey();

    long getTerm();

    long getFirstIndex();

    long getLastIndex();

    /**
     * @return the first application position, or -1 if the object holds no application entry
     */
    long getFirstPosition();

    /**
     * @return the last application position, or -1 if the object holds no application entry
     */
    long getLastPosition();

    long getEntryCount();

    long getSizeBytes();

    /**
     * @return the lowercase hex SHA-256 of the whole object
     */
    String getSha256();
  }

  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableArchivedSnapshotValue.Builder.class)
  interface ArchivedSnapshotValue {

    String getId();

    long getIndex();

    long getTerm();

    long getProcessedPosition();

    long getExportedPosition();

    long getLastFollowupEventPosition();

    long getMaxExportedPosition();

    long getFileCount();

    long getSizeBytes();
  }

  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableIndexChunkValue.Builder.class)
  interface IndexChunkValue {

    /**
     * @return the chunk number, starting at 1
     */
    long getChunkNo();

    String getKey();

    long getFirstIndex();

    long getLastIndex();

    long getFirstPosition();

    long getLastPosition();

    long getObjectCount();

    /**
     * @return the first covered checkpoint id, or -1 if none
     */
    long getFirstCheckpointId();

    /**
     * @return the last covered checkpoint id, or -1 if none
     */
    long getLastCheckpointId();

    String getSha256();
  }

  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableCoveredCheckpointValue.Builder.class)
  interface CoveredCheckpointValue {

    long getCheckpointId();

    long getCheckpointPosition();

    CheckpointType getCheckpointType();
  }
}
