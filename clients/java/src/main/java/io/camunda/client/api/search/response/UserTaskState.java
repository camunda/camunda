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
package io.camunda.client.api.search.response;

import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.UserTaskFilter;
import io.camunda.client.protocol.rest.UserTaskResult;

public enum UserTaskState {
  CREATED,
  COMPLETED,
  CANCELED,
  FAILED,
  UNKNOWN_ENUM_VALUE;

  public static UserTaskFilter.StateEnum toProtocolState(final UserTaskState value) {
    return (value == null) ? null : UserTaskFilter.StateEnum.fromValue(value.name());
  }

  public static UserTaskState fromProtocolState(final UserTaskResult.StateEnum value) {
    if (value == null) {
      return null;
    }
    try {
      return UserTaskState.valueOf(value.name());
    } catch (final IllegalArgumentException e) {
      EnumUtil.logUnknownEnumValue(value, "user task state", values());
      return UNKNOWN_ENUM_VALUE;
    }
  }
}
