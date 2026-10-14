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

/** A membership of {@code memberId} in the entity {@code targetId}. */
public interface PolicyMembershipEntryValue {

  /** The kind of the entity the member is added to or removed from. */
  PolicyUpdateEntityType getTargetType();

  /** The ID of the entity the member is added to or removed from. */
  String getTargetId();

  /** The kind of the member. */
  EntityType getMemberType();

  /** The ID of the member. */
  String getMemberId();
}
