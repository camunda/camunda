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
 * Records of the pull-based release lookup by which {@code P_K = hash(correlationKey)} — the
 * partition holding a correlation-key lock for a message-start instance created via the
 * cross-partition handshake — discovers whether its remote holder instance has completed on {@code
 * P_B = hash(businessId)}.
 *
 * <p>{@code P_K} sends a query carrying the {@link #getProcessInstanceKey() holder instance key};
 * on {@code P_B} that key's partition bits identify the destination, and {@code P_B} answers only
 * when the holder is no longer active. The reply is routed back to {@code P_K} via the partition
 * encoded in {@link #getRequestKey()} (a key generated on {@code P_K} when it dispatches the
 * query). The {@link #getBpmnProcessId() bpmnProcessId}, {@link #getCorrelationKey()
 * correlationKey} and {@link #getTenantId() tenantId} identify the lock entry to release and seed
 * the buffered-message rescan.
 *
 * <p>See {@link
 * io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent} for the
 * intents that drive the query/reply flow.
 */
@Value.Immutable
@ImmutableProtocol(
    builder = ImmutableMessageStartCorrelationKeyLockReleaseRecordValue.Builder.class)
public interface MessageStartCorrelationKeyLockReleaseRecordValue extends RecordValue, TenantOwned {

  /**
   * @return the key generated on {@code P_K} when it dispatched the query; encodes {@code P_K} in
   *     its partition bits so {@code P_B} can route the release reply back without a separate field
   */
  long getRequestKey();

  /**
   * @return the key of the holder process instance whose liveness on {@code P_B} is being queried;
   *     encodes {@code P_B} in its partition bits, identifying the partition the query targets
   */
  long getProcessInstanceKey();

  /**
   * @return the BPMN process id of the holder instance; together with {@link #getCorrelationKey()}
   *     identifies the correlation-key lock entry on {@code P_K} to release
   */
  String getBpmnProcessId();

  /**
   * @return the correlation key whose lock is held on {@code P_K}; the buffered-message rescan
   *     after release is scoped to this key
   */
  String getCorrelationKey();
}
