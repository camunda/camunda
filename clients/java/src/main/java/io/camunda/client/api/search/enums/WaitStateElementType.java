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
package io.camunda.client.api.search.enums;

public enum WaitStateElementType {
  AD_HOC_SUB_PROCESS,
  AD_HOC_SUB_PROCESS_INNER_INSTANCE,
  BOUNDARY_EVENT,
  BUSINESS_RULE_TASK,
  CALL_ACTIVITY,
  END_EVENT,
  EVENT_BASED_GATEWAY,
  EVENT_SUB_PROCESS,
  EXCLUSIVE_GATEWAY,
  INCLUSIVE_GATEWAY,
  INTERMEDIATE_CATCH_EVENT,
  INTERMEDIATE_THROW_EVENT,
  MANUAL_TASK,
  MULTI_INSTANCE_BODY,
  PARALLEL_GATEWAY,
  PROCESS,
  RECEIVE_TASK,
  SCRIPT_TASK,
  SEND_TASK,
  SEQUENCE_FLOW,
  SERVICE_TASK,
  START_EVENT,
  SUB_PROCESS,
  TASK,
  UNKNOWN,
  UNSPECIFIED,
  USER_TASK,
  UNKNOWN_ENUM_VALUE;
}
