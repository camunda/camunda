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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.response.BatchOperationItems;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.BatchOperationItemResponse;
import io.camunda.client.protocol.rest.BatchOperationItemSearchQueryResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class BatchOperationItemsImpl implements BatchOperationItems {

  private final List<BatchOperationItem> items;

  public BatchOperationItemsImpl(final BatchOperationItemSearchQueryResult queryResult) {
    items =
        queryResult.getItems().stream()
            .map(BatchOperationItemImpl::new)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
  }

  @Override
  public List<BatchOperationItem> items() {
    return items;
  }

  public static class BatchOperationItemImpl implements BatchOperationItem {

    private final String batchOperationKey;
    private final BatchOperationType operationType;
    private final Long itemKey;
    private final Long processInstanceKey;
    private final Long rootProcessInstanceKey;
    private final BatchOperationItemState status;
    private final OffsetDateTime processedDate;
    private final String errorMessage;

    public BatchOperationItemImpl(final BatchOperationItemResponse item) {
      batchOperationKey = item.getBatchOperationKey();
      operationType = EnumUtil.convert(item.getOperationType(), BatchOperationType.class);
      itemKey = ParseUtil.parseLongOrNull(item.getItemKey());
      processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
      rootProcessInstanceKey = ParseUtil.parseLongOrNull(item.getRootProcessInstanceKey());
      status = EnumUtil.convert(item.getState(), BatchOperationItemState.class);
      processedDate = ParseUtil.parseOffsetDateTimeOrNull(item.getProcessedDate());
      errorMessage = item.getErrorMessage();
    }

    @Override
    public String getBatchOperationKey() {
      return batchOperationKey;
    }

    @Override
    public BatchOperationType getOperationType() {
      return operationType;
    }

    @Override
    public Long getItemKey() {
      return itemKey;
    }

    @Override
    public Long getProcessInstanceKey() {
      return processInstanceKey;
    }

    @Override
    public Long getRootProcessInstanceKey() {
      return rootProcessInstanceKey;
    }

    @Override
    public OffsetDateTime getProcessedDate() {
      return processedDate;
    }

    @Override
    public String getErrorMessage() {
      return errorMessage;
    }

    @Override
    public BatchOperationItemState getStatus() {
      return status;
    }
  }
}
