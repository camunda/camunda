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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import org.immutables.value.Value;

/**
 * This record is used to mark a batch operation as "still in initialization" phase. It is needed
 * when the number of found items is too large for a single RecordBatch and must be split into
 * multiple runs.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableBatchOperationInitializationRecordValue.Builder.class)
public interface BatchOperationInitializationRecordValue
    extends BatchOperationRelated, RecordValue {
  /**
   * The last search result cursor that was used to find items for the batch operation.
   *
   * @return the search result cursor
   */
  String getSearchResultCursor();

  /**
   * The page size used for the search query that found items for the batch operation. When errors
   * occur during initialization, this value is used to reduce the pageSize.
   *
   * @return the current page size of the search query
   */
  int getSearchQueryPageSize();
}
