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

@Value.Immutable
@ImmutableProtocol(builder = ImmutableHistoryDeletionRecordValue.Builder.class)
public interface HistoryDeletionRecordValue extends RecordValue, TenantOwned {

  /**
   * The key of the resource to delete. Depending on the {@link HistoryDeletionType} this can be one
   * of four things:
   *
   * <ul>
   *   <li>{@link HistoryDeletionType#PROCESS_INSTANCE}: the process instance key
   *   <li>{@link HistoryDeletionType#PROCESS_DEFINITION}: the process definition key
   *   <li>{@link HistoryDeletionType#DECISION_INSTANCE}: the decision instance key
   *   <li>{@link HistoryDeletionType#DECISION_REQUIREMENTS}: the decision requirements key
   * </ul>
   *
   * @return the key of the resource
   */
  long getResourceKey();

  /** Returns the type of resource to delete. */
  HistoryDeletionType getResourceType();

  /**
   * Returns the process id which belongs to the process instance to delete.
   *
   * <p>This is only set when performing a singular deletion (not a batch operation) and is used for
   * performing the authorization checks. We cannot rely on the resource key for this as the
   * resource will be deleted from primary storage.
   */
  String getProcessId();

  /**
   * Returns the decision definition id which belongs to the decision instance to delete.
   *
   * <p>This is only set when performing a singular deletion (not a batch operation) and is used for
   * performing the authorization checks. We cannot rely on the resource key for this as the
   * resource will be deleted from primary storage.
   */
  String getDecisionDefinitionId();
}
