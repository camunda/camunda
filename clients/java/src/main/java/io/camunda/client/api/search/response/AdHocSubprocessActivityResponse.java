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
import io.camunda.client.protocol.rest.AdHocSubprocessActivityResult;
import java.util.List;

public interface AdHocSubprocessActivityResponse {

  List<AdHocSubprocessActivity> getItems();

  interface AdHocSubprocessActivity {

    Long getProcessDefinitionKey();

    String getProcessDefinitionId();

    String getAdHocSubprocessId();

    String getElementId();

    String getElementName();

    AdHocSubprocessActivityType getType();

    String getDocumentation();

    String getTenantId();

    enum AdHocSubprocessActivityType {
      UNSPECIFIED,
      PROCESS,
      SUB_PROCESS,
      EVENT_SUB_PROCESS,
      INTERMEDIATE_CATCH_EVENT,
      INTERMEDIATE_THROW_EVENT,
      BOUNDARY_EVENT,
      SERVICE_TASK,
      RECEIVE_TASK,
      USER_TASK,
      MANUAL_TASK,
      TASK,
      MULTI_INSTANCE_BODY,
      CALL_ACTIVITY,
      BUSINESS_RULE_TASK,
      SCRIPT_TASK,
      SEND_TASK,
      UNKNOWN,
      UNKNOWN_ENUM_VALUE;

      public static AdHocSubprocessActivityType fromProtocolType(
          final AdHocSubprocessActivityResult.TypeEnum value) {
        if (value == null) {
          return null;
        }

        try {
          return AdHocSubprocessActivityType.valueOf(value.name());
        } catch (final IllegalArgumentException e) {
          EnumUtil.logUnknownEnumValue(value, "ad-hoc subprocess activity type", values());
          return UNKNOWN_ENUM_VALUE;
        }
      }
    }
  }
}
