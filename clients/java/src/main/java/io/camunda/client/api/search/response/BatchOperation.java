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

import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.BatchOperationType;
import java.util.List;

public interface BatchOperation {

  // To be backwards compatible with legacy batch operations from Operate, we need a String Key
  // Operate BatchOperation Key is a UUID
  // Engine BatchOperation Key is a Long
  String getBatchOperationKey();

  BatchOperationState getStatus();

  BatchOperationType getType();

  String getStartDate();

  String getEndDate();

  Integer getOperationsTotalCount();

  Integer getOperationsFailedCount();

  Integer getOperationsCompletedCount();

  List<BatchOperationError> getErrors();
}
