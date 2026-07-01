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

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import org.immutables.value.Value;

/**
 * Engine Capability Version (ECV) record. Encodes a target cluster behavior version as a (line,
 * ordinal) pair. Submitted as a RAISE command, applied as an APPLIED event once a partition adopts
 * the target.
 *
 * <p>The {@code gatedField} demonstrates a write-site that emits a value only when the latest
 * applier version is in use; older replays of v1 APPLIED records have no such field.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableClusterVersionRecordValue.Builder.class)
public interface ClusterVersionRecordValue extends RecordValue {

  /** Release line, e.g. 89 for 8.9, 810 for 8.10. */
  int getLine();

  /** Ordinal on that line, monotonically increasing. */
  int getOrdinal();

  /** Demo gated field — written only by the v2 applier path. */
  String getGatedField();

  /**
   * Name of a {@code ClusterVersionCatalog.Feature} for SUPPRESS_FLAG / UNSUPPRESS_FLAG commands
   * and their resulting events. Empty for RAISE/APPLIED/PING/PINGED/ECHO/ECHOED.
   */
  String getFlagName();
}
