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

public enum BatchOperationType {
  PROCESS_CANCELLATION,

  /*  public static ProcessInstanceStateEnum toProtocolState(final BatchOperationType value) {
    return (value == null) ? null : ProcessInstanceStateEnum.fromValue(value.name());
  }

  public static BatchOperationType fromProtocolState(final ProcessInstanceStateEnum value) {
    if (value == null) {
      return null;
    }
    try {
      return BatchOperationType.valueOf(value.name());
    } catch (final IllegalArgumentException e) {
      EnumUtil.logUnknownEnumValue(value, "process instance state", values());
      return UNKNOWN_ENUM_VALUE;
    }
  }*/
}
