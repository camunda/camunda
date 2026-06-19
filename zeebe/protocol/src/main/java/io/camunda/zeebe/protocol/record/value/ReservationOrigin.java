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
 * Annotates how a {@code JobBatchRecord} reservation was triggered. Carried on the {@code
 * JobBatchRecord} alongside the existing batch metadata; populated only above {@code
 * Capability.JOB_BATCH_RESERVATION_ORIGIN} (catalog ordinal 18) — below that ordinal the field is
 * absent from the wire format altogether, which MsgPack's forward-compatible record encoding
 * tolerates by skipping unknown properties.
 *
 * <p>This is the demo paired with {@code JobKind.MAINTENANCE}: there, a new value joins an existing
 * enum; here, a new enum joins an existing record. Both demonstrate facets of the "previous version
 * not aware" rolling-upgrade hazard. The new-enum case is the gentler of the two — old binaries
 * don't crash on an unknown property name because the surrounding property is dropped before its
 * value is decoded — but they still lose the information the field carried, so behavior downstream
 * of the field (audit, exporter routing, reclaim policy) silently diverges if it depended on the
 * field's value.
 *
 * <p>A future ordinal that extends this enum with a third value (e.g. {@code ENGINE_INITIATED} for
 * reservations the engine creates on behalf of a watchdog) would put the same {@code Enum.valueOf}
 * hazard as {@code JobKind.MAINTENANCE} in play: an old binary at ordinal 18 has this class with
 * just {@code UNSPECIFIED} and {@code WORKER_REQUEST}, and would crash on {@code
 * Enum.valueOf("ENGINE_INITIATED")}. That extension would be guarded by its own catalog ordinal.
 */
public enum ReservationOrigin {

  /**
   * Default sentinel returned when the field is absent from the record (e.g. a record produced
   * below the gate, or a foreign record that omits the field for any other reason). Producers must
   * not write this value explicitly — they should either set a meaningful value above the gate or
   * leave the field unset below.
   */
  UNSPECIFIED,

  /**
   * The reservation was triggered by a worker's {@code ActivateJobs} call. This is the only value
   * the {@code JobBatchActivateProcessor} writes today; it's the placeholder for the next value
   * that lands as a follow-up ordinal extension.
   */
  WORKER_REQUEST
}
