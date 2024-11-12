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
package io.camunda.zeebe.client.impl.search.response;

import io.camunda.zeebe.client.api.search.response.Operation;
import io.camunda.zeebe.client.protocol.rest.OperationItem;
import java.util.Optional;

public class OperationImpl implements Operation {

  private final String id;
  private final String batchOperationId;
  private final String type;
  private final String state;
  private final String errorMessage;
  private final String completedDate;

  public OperationImpl(final OperationItem item) {
    this.id = item.getId();
    this.batchOperationId = item.getBatchOperationId();
    this.type = Optional.ofNullable(item.getType()).map(Enum::toString).orElse(null);
    this.state = Optional.ofNullable(item.getState()).map(Enum::toString).orElse(null);
    this.errorMessage = item.getErrorMessage();
    this.completedDate = item.getCompletedDate();
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getBatchOperationId() {
    return batchOperationId;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getState() {
    return state;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public String getCompletedDate() {
    return completedDate;
  }
}
