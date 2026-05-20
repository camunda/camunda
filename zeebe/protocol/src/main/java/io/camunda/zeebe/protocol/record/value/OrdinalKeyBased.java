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
 * Marks a {@link io.camunda.zeebe.protocol.record.RecordValue} as belonging to a logical ordinal
 * grouping used by the exporter to route records into ordinal-scoped indices instead of the legacy
 * main index.
 *
 * <p>An ordinal is a monotonic counter assigned to a root process instance at creation time and
 * propagated to every record that participates in that instance's hierarchy.
 *
 * <p>The value {@code 0} is reserved to indicate that a record must be stored in the legacy (older
 * style) main index. This applies to records that pre-date the archiverless exporter rollout, and
 * can also be used to explicitly force a record into the main index.
 *
 * <p>The value {@code -1} means the record is not ordinal-controlled and does not participate in
 * ordinal-based routing at all. TODO(yohanfernando): verify this behaviour.
 *
 * <p>Engine-assigned ordinals start at {@code 1}.
 */
public interface OrdinalKeyBased {

  /**
   * @return the ordinal this record belongs to; {@code 0} means the record must be stored in the
   *     main index (either a legacy record or explicitly forced); {@code -1} means the record is
   *     not ordinal-controlled
   */
  int getOrdinalKey();
}
