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
package io.camunda.client.impl.search.sort;

import io.camunda.client.api.search.sort.BatchOperationItemSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class BatchOperationItemSortImpl extends SearchRequestSortBase<BatchOperationItemSort>
    implements BatchOperationItemSort {

  @Override
  public BatchOperationItemSort batchOperationKey() {
    return field("batchOperationKey");
  }

  @Override
  public BatchOperationItemSort state() {
    return field("state");
  }

  @Override
  public BatchOperationItemSort itemKey() {
    return field("itemKey");
  }

  @Override
  public BatchOperationItemSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  protected BatchOperationItemSort self() {
    return this;
  }
}
