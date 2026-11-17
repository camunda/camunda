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
import org.immutables.value.Value;

/**
 * Identifies one authorization to delete as part of a policy update. Hub identifies an
 * authorization by its owner and the resource it applies to, not by its engine key, so the delete
 * carries exactly that identity and no permissions.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutablePolicyAuthorizationDeleteEntryValue.Builder.class)
public interface PolicyAuthorizationDeleteEntryValue {

  /** The kind of the owner of the authorization. */
  AuthorizationOwnerType getOwnerType();

  /** The ID of the owner of the authorization. */
  String getOwnerId();

  /** The type of the resource the authorization applies to. */
  AuthorizationResourceType getResourceType();

  /** How the authorization matches resources. */
  AuthorizationResourceMatcher getResourceMatcher();

  /** The ID of the resource, or empty if the matcher does not use one. */
  String getResourceId();

  /** The name of the resource property, or empty if the matcher does not use one. */
  String getResourcePropertyName();
}
