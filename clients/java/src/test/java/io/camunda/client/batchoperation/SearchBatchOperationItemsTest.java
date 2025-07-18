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
package io.camunda.client.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.protocol.rest.*;
import io.camunda.client.protocol.rest.BatchOperationItemSearchQuerySortRequest.FieldEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

class SearchBatchOperationItemsTest extends ClientRestTest {

  @Test
  void shouldSearchBatchOperation() {
    // when
    client
        .newBatchOperationItemsSearchRequest()
        .filter(f -> f.batchOperationKey("123"))
        .sort(s -> s.state().asc())
        .send()
        .join();

    // then
    final LoggedRequest restRequest = RestGatewayService.getLastRequest();
    assertThat(restRequest.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(restRequest.getUrl()).isEqualTo("/v2/batch-operation-items/search");
    assertThat(restRequest.getBodyAsString())
        .isEqualTo(
            "{\"sort\":[{\"field\":\"state\",\"order\":\"ASC\"}],\"filter\":{\"batchOperationKey\":{\"$eq\":\"123\",\"$in\":[],\"$notIn\":[]}}}");
  }

  @Test
  void shouldSearchBatchOperationWithoutFilter() {
    // when
    client.newBatchOperationItemsSearchRequest().send().join();

    // then
    final BatchOperationItemSearchQuery request =
        gatewayService.getLastRequest(BatchOperationItemSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchBatchOperationWithFullFilters() {
    // when
    client
        .newBatchOperationItemsSearchRequest()
        .filter(
            f ->
                f.batchOperationKey("123")
                    .state(BatchOperationItemState.ACTIVE)
                    .processInstanceKey(123L)
                    .itemKey(456L))
        .send()
        .join();

    // then
    final BatchOperationItemSearchQuery request =
        gatewayService.getLastRequest(BatchOperationItemSearchQuery.class);
    assertThat(request.getFilter().getBatchOperationKey().get$Eq()).isEqualTo("123");
    assertThat(request.getFilter().getState().get$Eq())
        .isEqualTo(BatchOperationItemStateEnum.ACTIVE);
    assertThat(request.getFilter().getProcessInstanceKey().get$Eq()).isEqualTo("123");
    assertThat(request.getFilter().getItemKey().get$Eq()).isEqualTo("456");
  }

  @Test
  void shouldSearchBatchOperationWithFullSorting() {
    // when
    client
        .newBatchOperationItemsSearchRequest()
        .sort(
            s ->
                s.batchOperationKey()
                    .asc()
                    .state()
                    .asc()
                    .processInstanceKey()
                    .desc()
                    .itemKey()
                    .asc())
        .send()
        .join();

    // then
    final BatchOperationItemSearchQuery request =
        gatewayService.getLastRequest(BatchOperationItemSearchQuery.class);
    assertThat(request.getSort().size()).isEqualTo(4);
    assertThat(request.getSort().get(0).getField()).isEqualTo(FieldEnum.BATCH_OPERATION_KEY);
    assertThat(request.getSort().get(0).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(1).getField()).isEqualTo(FieldEnum.STATE);
    assertThat(request.getSort().get(1).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(2).getField()).isEqualTo(FieldEnum.PROCESS_INSTANCE_KEY);
    assertThat(request.getSort().get(2).getOrder()).isEqualTo(SortOrderEnum.DESC);
    assertThat(request.getSort().get(3).getField()).isEqualTo(FieldEnum.ITEM_KEY);
    assertThat(request.getSort().get(3).getOrder()).isEqualTo(SortOrderEnum.ASC);
  }
}
