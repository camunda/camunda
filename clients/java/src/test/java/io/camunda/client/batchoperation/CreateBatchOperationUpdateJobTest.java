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
import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.protocol.rest.BatchOperationCreatedResult;
import io.camunda.client.protocol.rest.JobBatchUpdateRequest;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.Duration;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public final class CreateBatchOperationUpdateJobTest extends ClientRestTest {

  @Test
  public void shouldSendUpdateJobBatchWithPriority() {
    // given
    gatewayService.onUpdateJobsBatchRequest(
        Instancio.create(BatchOperationCreatedResult.class).batchOperationKey("42"));

    // when
    client
        .newCreateBatchOperationCommand()
        .updateJob()
        .priority(64)
        .filter(f -> f.type("payment"))
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(request.getUrl()).isEqualTo("/v2/jobs/batch-update");

    final JobBatchUpdateRequest lastRequest =
        gatewayService.getLastRequest(JobBatchUpdateRequest.class);
    assertThat(lastRequest.getFilter()).isNotNull();
    assertThat(lastRequest.getChangeset()).isNotNull();
    assertThat(lastRequest.getChangeset().getPriority()).isEqualTo(64);
    assertThat(lastRequest.getChangeset().getRetries()).isNull();
    assertThat(lastRequest.getChangeset().getTimeout()).isNull();
  }

  @Test
  public void shouldSendUpdateJobBatchWithRetries() {
    // given
    gatewayService.onUpdateJobsBatchRequest(
        Instancio.create(BatchOperationCreatedResult.class).batchOperationKey("42"));

    // when
    client
        .newCreateBatchOperationCommand()
        .updateJob()
        .retries(3)
        .filter(f -> f.type("payment"))
        .send()
        .join();

    // then
    final JobBatchUpdateRequest lastRequest =
        gatewayService.getLastRequest(JobBatchUpdateRequest.class);
    assertThat(lastRequest.getChangeset().getRetries()).isEqualTo(3);
    assertThat(lastRequest.getChangeset().getPriority()).isNull();
    assertThat(lastRequest.getChangeset().getTimeout()).isNull();
  }

  @Test
  public void shouldSendUpdateJobBatchWithTimeout() {
    // given
    gatewayService.onUpdateJobsBatchRequest(
        Instancio.create(BatchOperationCreatedResult.class).batchOperationKey("42"));

    // when
    client
        .newCreateBatchOperationCommand()
        .updateJob()
        .timeout(30000L)
        .filter(f -> f.type("payment"))
        .send()
        .join();

    // then
    final JobBatchUpdateRequest lastRequest =
        gatewayService.getLastRequest(JobBatchUpdateRequest.class);
    assertThat(lastRequest.getChangeset().getTimeout()).isEqualTo(30000L);
    assertThat(lastRequest.getChangeset().getPriority()).isNull();
    assertThat(lastRequest.getChangeset().getRetries()).isNull();
  }

  @Test
  public void shouldSendUpdateJobBatchWithTimeoutDuration() {
    // given
    gatewayService.onUpdateJobsBatchRequest(
        Instancio.create(BatchOperationCreatedResult.class).batchOperationKey("42"));

    // when
    client
        .newCreateBatchOperationCommand()
        .updateJob()
        .timeout(Duration.ofSeconds(30))
        .filter(f -> f.type("payment"))
        .send()
        .join();

    // then
    final JobBatchUpdateRequest lastRequest =
        gatewayService.getLastRequest(JobBatchUpdateRequest.class);
    assertThat(lastRequest.getChangeset().getTimeout()).isEqualTo(30000L);
  }

  @Test
  public void shouldSendUpdateJobBatchWithAllChangesetFields() {
    // given
    gatewayService.onUpdateJobsBatchRequest(
        Instancio.create(BatchOperationCreatedResult.class).batchOperationKey("42"));

    // when
    client
        .newCreateBatchOperationCommand()
        .updateJob()
        .priority(64)
        .retries(3)
        .timeout(30000L)
        .filter(f -> f.type("payment"))
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(request.getUrl()).isEqualTo("/v2/jobs/batch-update");

    final JobBatchUpdateRequest lastRequest =
        gatewayService.getLastRequest(JobBatchUpdateRequest.class);
    assertThat(lastRequest.getFilter()).isNotNull();
    assertThat(lastRequest.getChangeset().getPriority()).isEqualTo(64);
    assertThat(lastRequest.getChangeset().getRetries()).isEqualTo(3);
    assertThat(lastRequest.getChangeset().getTimeout()).isEqualTo(30000L);
  }

  @Test
  public void shouldDeserializeUpdateJobBatchResponse() {
    // given
    final BatchOperationCreatedResult apiResult =
        Instancio.create(BatchOperationCreatedResult.class)
            .batchOperationKey("99")
            .batchOperationType(io.camunda.client.protocol.rest.BatchOperationTypeEnum.UPDATE_JOB);
    gatewayService.onUpdateJobsBatchRequest(apiResult);

    // when
    final CreateBatchOperationResponse response =
        client
            .newCreateBatchOperationCommand()
            .updateJob()
            .priority(10)
            .filter(f -> f.type("payment"))
            .send()
            .join();

    // then
    assertThat(response.getBatchOperationKey()).isEqualTo("99");
    assertThat(response.getBatchOperationType()).isEqualTo(BatchOperationType.UPDATE_JOB);
  }
}
