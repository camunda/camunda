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
import java.util.Collection;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableIdentitySetupRecordValue.Builder.class)
public interface IdentitySetupRecordValue extends RecordValue {

  Collection<RoleRecordValue> getRoles();

  Collection<RoleRecordValue> getRoleMembers();

  List<UserRecordValue> getUsers();

  /**
   * @deprecated Remove after 8.x release. Use {@link #getTenants()} instead.
   */
  @Deprecated
  TenantRecordValue getDefaultTenant();

  List<TenantRecordValue> getTenants();

  Collection<TenantRecordValue> getTenantMembers();

  List<MappingRuleRecordValue> getMappingRules();

  Collection<AuthorizationRecordValue> getAuthorizations();

  Collection<GroupRecordValue> getGroups();

  Collection<GroupRecordValue> getGroupMembers();
}
