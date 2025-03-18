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

import io.camunda.client.api.search.response.BatchOperationItems;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.BatchOperationItemResponse;
import io.camunda.client.protocol.rest.BatchOperationItemSearchQueryResult;
import java.util.ArrayList;
import java.util.List;

public class BatchOperationItemsImpl implements BatchOperationItems {

  private final List<BatchOperationItem> items;

  public BatchOperationItemsImpl(final BatchOperationItemSearchQueryResult queryResult) {
    assert queryResult.getItems() != null; // TODO
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

    private final Long batchOperationKey;

    private final Long itemKey;

    private final String state;

    public BatchOperationItemImpl(final BatchOperationItemResponse item) {
      this.batchOperationKey = ParseUtil.parseLongOrNull(item.getBatchOperationKey());
      this.itemKey = ParseUtil.parseLongOrNull(item.getItemKey());
      this.state = item.getState();
    }

    @Override
    public Long getBatchOperationKey() {
      return batchOperationKey;
    }

    @Override
    public Long getKey() {
      return itemKey;
    }

    @Override
    public String getState() {
      return state;
    }
  }
}
