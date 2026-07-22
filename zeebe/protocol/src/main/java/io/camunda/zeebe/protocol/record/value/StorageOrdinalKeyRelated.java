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
package io.camunda.zeebe.protocol.record.value;

/**
 * Marks a record value that carries a storage ordinal key.
 *
 * <p>A storage ordinal groups entities with a well-defined lifetime (e.g. process instances,
 * standalone decision instances - anything that is archived today) so that their data can be stored
 * together and disposed of together. The engine assigns the currently active ordinal to a root
 * process instance when it is created; all dependent records (child instances, jobs, variables,
 * incidents, etc.) inherit the ordinal of their root instance. Once assigned, the ordinal of a root
 * process instance never changes.
 *
 * <p>Exporters use this key to route a record's document to the storage location (e.g. an
 * ordinal-suffixed index) of its ordinal. Once every entity in an ordinal has completed and the
 * retention period has elapsed, the ordinal's storage location can be deleted as a whole, removing
 * the need to archive (i.e. move) documents of completed instances.
 *
 * <p>Ordinals are monotonically increasing numbers, with a few reserved values:
 *
 * <ul>
 *   <li>{@code 0} — the main (non-ordinal) index of the record type. Used while ordinal-based
 *       storage is disabled, and for instances that predate the feature; such records keep being
 *       written to the main index and remain subject to archiving.
 *   <li>{@code 1..1000} — reserved for special allocations (e.g. fixed ordinal pools or recovery).
 *   <li>{@code 1001+} — regular ordinals assigned by the engine.
 * </ul>
 */
public interface StorageOrdinalKeyRelated {

  /**
   * @return the ordinal this record belongs to; {@code 0} means the record must be stored in the
   *     main index (either a legacy record or explicitly forced); {@code -1} means the record is
   *     not ordinal-controlled
   */
  int getStorageOrdinalKey();
}
