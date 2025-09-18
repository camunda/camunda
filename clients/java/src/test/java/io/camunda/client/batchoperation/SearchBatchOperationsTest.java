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
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.protocol.rest.*;
import io.camunda.client.protocol.rest.BatchOperationSearchQuerySortRequest.FieldEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

class SearchBatchOperationsTest extends ClientRestTest {

  @Test
  void shouldSearchBatchOperation() {
    // when
    client
        .newBatchOperationSearchRequest()
        .filter(f -> f.batchOperationKey("123"))
        .sort(s -> s.state().asc())
        .withDefaultConsistencyPolicy()
        .send()
        .join();

    // then
    final LoggedRequest restRequest = RestGatewayService.getLastRequest();
    assertThat(restRequest.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(restRequest.getUrl()).isEqualTo("/v2/batch-operations/search");
    assertThat(restRequest.getBodyAsString())
        .isEqualTo(
            "{\"sort\":[{\"field\":\"state\",\"order\":\"ASC\"}],\"filter\":{\"batchOperationKey\":{\"$eq\":\"123\",\"$in\":[],\"$notIn\":[]}}}");
  }

  @Test
  void shouldSearchBatchOperationWithoutFilter() {
    // when
    client.newBatchOperationSearchRequest().withDefaultConsistencyPolicy().send().join();

    // then
    final BatchOperationSearchQuery request =
        gatewayService.getLastRequest(BatchOperationSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchBatchOperationWithFullFilters() {
    // when
    client
        .newBatchOperationSearchRequest()
        .filter(
            f ->
                f.batchOperationKey("123")
                    .state(BatchOperationState.ACTIVE)
                    .operationType(BatchOperationType.CANCEL_PROCESS_INSTANCE))
        .withDefaultConsistencyPolicy()
        .send()
        .join();

    // then
    final BatchOperationSearchQuery request =
        gatewayService.getLastRequest(BatchOperationSearchQuery.class);
    assertThat(request.getFilter().getBatchOperationKey().get$Eq()).isEqualTo("123");
    assertThat(request.getFilter().getState().get$Eq()).isEqualTo(BatchOperationStateEnum.ACTIVE);
    assertThat(request.getFilter().getOperationType().get$Eq())
        .isEqualTo(BatchOperationTypeEnum.CANCEL_PROCESS_INSTANCE);
  }

  @Test
  void shouldSearchBatchOperationWithFullSorting() {
    // when
    client
        .newBatchOperationSearchRequest()
        .sort(
            s ->
                s.batchOperationKey()
                    .asc()
                    .state()
                    .asc()
                    .startDate()
                    .desc()
                    .endDate()
                    .asc()
                    .operationType()
                    .asc())
        .withDefaultConsistencyPolicy()
        .send()
        .join();

    // then
    final BatchOperationSearchQuery request =
        gatewayService.getLastRequest(BatchOperationSearchQuery.class);
    assertThat(request.getSort().size()).isEqualTo(5);
    assertThat(request.getSort().get(0).getField())
        .isEqualTo(BatchOperationSearchQuerySortRequest.FieldEnum.BATCH_OPERATION_KEY);
    assertThat(request.getSort().get(0).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(1).getField()).isEqualTo(FieldEnum.STATE);
    assertThat(request.getSort().get(1).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(2).getField()).isEqualTo(FieldEnum.START_DATE);
    assertThat(request.getSort().get(2).getOrder()).isEqualTo(SortOrderEnum.DESC);
    assertThat(request.getSort().get(3).getField()).isEqualTo(FieldEnum.END_DATE);
    assertThat(request.getSort().get(3).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(4).getField()).isEqualTo(FieldEnum.OPERATION_TYPE);
    assertThat(request.getSort().get(4).getOrder()).isEqualTo(SortOrderEnum.ASC);
  }
}
