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
 * A policy update, which brings a partition from {@link #getBasePolicyVersion()} to {@link
 * #getPolicyVersion()}. Its entities are split into chunks that each stay below the record size
 * limit, in apply order, and the chunks share the update's {@link #getUpdateId()}. The last chunk
 * is the one whose {@link #getChunkIndex()} is {@code getChunkCount() - 1}.
 *
 * <p>Only an {@code APPEND_CHUNK} command transports policy data: it carries one chunk. The other
 * intents control the update and carry only its identifiers.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutablePolicyUpdateRecordValue.Builder.class)
public interface PolicyUpdateRecordValue extends RecordValue {

  /** The ID of the policy the update belongs to. */
  String getPolicyId();

  /** The policy version the update establishes once it is committed. */
  long getPolicyVersion();

  /** The policy version the update is derived from. */
  long getBasePolicyVersion();

  /** The Hub sequence number the change set was polled up to. */
  long getSeqNo();

  /** The ID that groups the chunks of one update. */
  String getUpdateId();

  /**
   * The 0-based index of this chunk, which is also its position in the apply order, or -1 if not
   * set.
   */
  int getChunkIndex();

  /** The total number of chunks of the update, or -1 if not set. */
  int getChunkCount();

  List<TenantRecordValue> getTenants();

  List<RoleRecordValue> getRoles();

  List<GroupRecordValue> getGroups();

  List<MappingRuleRecordValue> getMappingRules();

  List<PolicyMembershipEntryValue> getMemberships();

  List<AuthorizationRecordValue> getAuthorizations();

  List<PolicyDeleteEntryValue> getDeletes();

  List<PolicyMembershipEntryValue> getMembershipDeletes();

  List<PolicyAuthorizationDeleteEntryValue> getAuthorizationDeletes();

  /** Why the update failed or was aborted, or empty if it did not. */
  String getErrorMessage();
}
