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
@ImmutableProtocol(builder = ImmutableGroupRecordValue.Builder.class)
public interface GroupRecordValue extends RecordValue {

  /** The internal key of a group. */
  long getGroupKey();

  /** The unique identifier of the group within our system. */
  String getGroupId();

  /** The name of the group. */
  String getName();

  /** The description of the group. */
  String getDescription();

  /** The ID of a user/mapping rule to assign/remove from a group. */
  String getEntityId();

  /** The type of the entity to assign/remove from a group. */
  EntityType getEntityType();
}
