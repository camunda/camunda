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
package io.camunda.client.wrappers;

import io.camunda.client.impl.util.EnumUtil;

public enum ResourceType {
  AUTHORIZATION,
  MAPPING_RULE,
  MESSAGE,
  BATCH,
  APPLICATION,
  SYSTEM,
  TENANT,
  RESOURCE,
  PROCESS_DEFINITION,
  DECISION_REQUIREMENTS_DEFINITION,
  DECISION_DEFINITION,
  GROUP,
  USER,
  ROLE,
  UNKNOWN_ENUM_VALUE;

  public static io.camunda.client.protocol.rest.ResourceTypeEnum toProtocolEnum(
      final ResourceType value) {
    return (value == null)
        ? null
        : io.camunda.client.protocol.rest.ResourceTypeEnum.fromValue(value.name());
  }

  public static ResourceType fromProtocolEnum(
      final io.camunda.client.protocol.rest.ResourceTypeEnum value) {
    if (value == null) {
      return null;
    }
    try {
      return ResourceType.valueOf(value.name());
    } catch (final IllegalArgumentException e) {
      EnumUtil.logUnknownEnumValue(value, value.getClass().getName(), values());
      return UNKNOWN_ENUM_VALUE;
    }
  }
}
