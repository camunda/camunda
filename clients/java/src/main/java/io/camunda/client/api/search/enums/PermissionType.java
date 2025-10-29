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

public enum PermissionType {
  ACCESS,
  CREATE,
  CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE,
  CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE,
  CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE,
  CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE,
  CREATE_BATCH_OPERATION_RESOLVE_INCIDENT,
  CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE,
  CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION,
  CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION,
  CREATE_PROCESS_INSTANCE,
  CREATE_DECISION_INSTANCE,
  READ,
  READ_PROCESS_INSTANCE,
  READ_USER_TASK,
  READ_DECISION_INSTANCE,
  READ_PROCESS_DEFINITION,
  READ_DECISION_DEFINITION,
  READ_USAGE_METRIC,
  UPDATE,
  UPDATE_PROCESS_INSTANCE,
  UPDATE_USER_TASK,
  CANCEL_PROCESS_INSTANCE,
  MODIFY_PROCESS_INSTANCE,
  CLAIM,
  COMPLETE,
  DELETE,
  DELETE_PROCESS,
  DELETE_DRD,
  DELETE_FORM,
  DELETE_RESOURCE,
  DELETE_PROCESS_INSTANCE,
  DELETE_DECISION_INSTANCE,
  UNKNOWN_ENUM_VALUE;
}
