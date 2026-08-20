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
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.response.BatchOperation;
import io.camunda.client.protocol.rest.BatchOperationResponse;
import io.camunda.client.protocol.rest.BatchOperationStateEnum;
import io.camunda.client.protocol.rest.BatchOperationTypeEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class QueryBatchOperationTest extends ClientRestTest {

  @Test
  public void shouldGetBatchOperationByKey() {
    // given
    final String batchOperationKey = "123";
    final OffsetDateTime dateTime = OffsetDateTime.now();
    gatewayService.onBatchOperationRequest(
        batchOperationKey,
        Instancio.create(BatchOperationResponse.class)
            .batchOperationKey(batchOperationKey)
            .state(BatchOperationStateEnum.UNKNOWN_DEFAULT_OPEN_API)
            .batchOperationType(BatchOperationTypeEnum.UNKNOWN_DEFAULT_OPEN_API)
            .endDate(dateTime.toString())
            .startDate(dateTime.toString())
            .actorType(null)
            .actorId(null));

    // when
    final BatchOperation result =
        client.newBatchOperationGetRequest(batchOperationKey).send().join();

    // then it sends the correct request
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
    assertThat(request.getUrl()).isEqualTo("/v2/batch-operations/123");
    assertThat(request.getBodyAsString()).isEmpty();

    // and maps the response correctly
    assertThat(result.getBatchOperationKey()).isEqualTo("123");
    assertThat(result.getType()).isEqualTo(BatchOperationType.UNKNOWN_ENUM_VALUE);
    assertThat(result.getStatus()).isEqualTo(BatchOperationState.UNKNOWN_ENUM_VALUE);
    assertThat(result.getStartDate()).isEqualTo(dateTime);
    assertThat(result.getEndDate()).isEqualTo(dateTime);

    assertThat(result.getActorType()).isNull();
    assertThat(result.getActorId()).isNull();
  }

  @Test
  public void shouldIncludeActorInfoInGetBatchOperationResponse() {
    // given
    final String batchOperationKey = "123";

    gatewayService.onBatchOperationRequest(
        batchOperationKey,
        Instancio.create(BatchOperationResponse.class)
            .batchOperationKey(batchOperationKey)
            .actorType(io.camunda.client.protocol.rest.AuditLogActorTypeEnum.USER)
            .actorId("demo-user")
            .endDate(OffsetDateTime.now().toString())
            .startDate(OffsetDateTime.now().toString()));

    // when
    final BatchOperation result =
        client.newBatchOperationGetRequest(batchOperationKey).send().join();

    // then
    assertThat(result.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(result.getActorId()).isEqualTo("demo-user");
  }
}
