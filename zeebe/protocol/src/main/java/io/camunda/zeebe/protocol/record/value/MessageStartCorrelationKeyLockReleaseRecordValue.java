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
import java.util.List;
import org.immutables.value.Value;

/**
 * Records of the pull-based release lookup by which {@code P_K = hash(correlationKey)} — the
 * partition holding correlation-key locks for message-start instances created via the
 * cross-partition handshake — discovers whether their remote holder instances have completed on
 * {@code P_B = hash(businessId)}.
 *
 * <p>{@code P_K} batches a query carrying one {@link MessageStartLockReleaseHolderValue holder} per
 * lock entry it polls for. The holders in a single query all target the same {@code P_B} (the
 * partition identified by each {@link MessageStartLockReleaseHolderValue#getProcessInstanceKey()
 * holder instance key}'s partition bits), so one query is dispatched per target partition rather
 * than one per lock. {@code P_B} answers with a {@code RELEASE} reply — itself carrying the single
 * gone holder — for each holder it finds is no longer active, and stays silent for the rest. The
 * reply is routed back to {@code P_K} via the partition encoded in {@link #getRequestKey()} (a key
 * generated on {@code P_K} when it dispatches the query).
 *
 * <p>See {@link
 * io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent} for the
 * intents that drive the query/reply flow.
 */
@Value.Immutable
@ImmutableProtocol(
    builder = ImmutableMessageStartCorrelationKeyLockReleaseRecordValue.Builder.class)
public interface MessageStartCorrelationKeyLockReleaseRecordValue extends RecordValue {

  /**
   * @return the key generated on {@code P_K} when it dispatched the query; encodes {@code P_K} in
   *     its partition bits so {@code P_B} can route each release reply back without a separate
   *     field
   */
  long getRequestKey();

  /**
   * @return the holder instances whose liveness on {@code P_B} is being queried (or, on a {@code
   *     RELEASE} reply, the single holder found to be gone). All holders of a query target the same
   *     {@code P_B}.
   */
  List<MessageStartLockReleaseHolderValue> getHolders();

  /**
   * A single correlation-key lock entry on {@code P_K} whose holder instance lives on {@code P_B}.
   * Carries everything the release path needs without a further cross-partition lookup: the holder
   * instance key (its partition bits identify {@code P_B}), and the {@code bpmnProcessId} + {@code
   * correlationKey} + {@code tenantId} that identify the lock entry to release and seed the
   * buffered-message rescan once the holder is gone.
   */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableMessageStartLockReleaseHolderValue.Builder.class)
  interface MessageStartLockReleaseHolderValue extends TenantOwned {

    /**
     * @return the key of the holder process instance whose liveness on {@code P_B} is being
     *     queried; encodes {@code P_B} in its partition bits, identifying the partition the query
     *     targets
     */
    long getProcessInstanceKey();

    /**
     * @return the BPMN process id of the holder instance; together with {@link
     *     #getCorrelationKey()} identifies the correlation-key lock entry on {@code P_K} to release
     */
    String getBpmnProcessId();

    /**
     * @return the correlation key whose lock is held on {@code P_K}; the buffered-message rescan
     *     after release is scoped to this key
     */
    String getCorrelationKey();
  }
}
