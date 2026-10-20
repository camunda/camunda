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
 * One chunk of a policy update, or the progress of applying it. Chunks carry sections of the update
 * in apply order. The cursor fields are used by the apply and applied records only.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutablePolicyUpdateRecordValue.Builder.class)
public interface PolicyUpdateRecordValue extends RecordValue {

  /** The ID of the update, as sent by the Hub. */
  String getEnvelopeId();

  /** Whether the update is a full snapshot or a delta. */
  PolicyUpdateKind getKind();

  /** The version the update establishes once it is switched in. */
  long getPolicyVersion();

  /** The 0-based index of this chunk. */
  int getChunkIndex();

  /** The total number of chunks of the update. */
  int getChunkCount();

  List<TenantRecordValue> getTenants();

  List<RoleRecordValue> getRoles();

  List<GroupRecordValue> getGroups();

  List<MappingRuleRecordValue> getMappingRules();

  List<PolicyMembershipEntryValue> getMemberships();

  List<AuthorizationRecordValue> getAuthorizations();

  List<PolicyDeleteEntryValue> getDeletes();

  List<PolicyMembershipEntryValue> getMembershipDeletes();

  List<AuthorizationRecordValue> getAuthorizationDeletes();

  /** Whether this page is the last one of the update. */
  boolean isLastPage();
}
