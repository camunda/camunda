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
package io.camunda.client.resource.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.DeleteResourceCommandStep1;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeleteResourceResponse;
import io.camunda.client.protocol.rest.BatchOperationCreatedResult;
import io.camunda.client.protocol.rest.BatchOperationTypeEnum;
import io.camunda.client.protocol.rest.DeleteResourceRequest;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import org.junit.jupiter.api.Test;

public class DeleteResourceRestTest extends ClientRestTest {

  private static final io.camunda.client.protocol.rest.DeleteResourceResponse DUMMY_RESPONSE =
      new io.camunda.client.protocol.rest.DeleteResourceResponse().resourceKey("123");

  private static final io.camunda.client.protocol.rest.DeleteResourceResponse
      DUMMY_RESPONSE_WITH_BATCH =
          new io.camunda.client.protocol.rest.DeleteResourceResponse()
              .resourceKey("123")
              .batchOperation(
                  new BatchOperationCreatedResult()
                      .batchOperationKey("34")
                      .batchOperationType(BatchOperationTypeEnum.DELETE_PROCESS_INSTANCE));

  @Test
  public void shouldSendCommand() {
    // given
    final long resourceKey = 123L;
    gatewayService.onDeleteResourceRequest(resourceKey, DUMMY_RESPONSE);

    // when
    client.newDeleteResourceCommand(resourceKey).send().join();

    // then
    final DeleteResourceRequest request =
        gatewayService.getLastRequest(DeleteResourceRequest.class);
    assertThat(request).isNotNull();
    assertThat(request.getDeleteHistory()).isFalse();
  }

  @Test
  public void shouldSendCommandWithDeleteHistoryFalse() {
    // given
    final long resourceKey = 123L;
    gatewayService.onDeleteResourceRequest(resourceKey, DUMMY_RESPONSE);

    // when
    client.newDeleteResourceCommand(resourceKey).deleteHistory(false).send().join();

    // then
    final DeleteResourceRequest request =
        gatewayService.getLastRequest(DeleteResourceRequest.class);
    assertThat(request).isNotNull();
    assertThat(request.getDeleteHistory()).isFalse();
  }

  @Test
  public void shouldSendCommandWithDeleteHistoryTrue() {
    // given
    final long resourceKey = 123L;
    gatewayService.onDeleteResourceRequest(resourceKey, DUMMY_RESPONSE_WITH_BATCH);

    // when
    client.newDeleteResourceCommand(resourceKey).deleteHistory(true).send().join();

    // then
    final DeleteResourceRequest request =
        gatewayService.getLastRequest(DeleteResourceRequest.class);
    assertThat(request).isNotNull();
    assertThat(request.getDeleteHistory()).isTrue();
  }

  @Test
  public void shouldSetOperationReference() {
    // given
    final long resourceKey = 123L;
    gatewayService.onDeleteResourceRequest(resourceKey, DUMMY_RESPONSE);
    final long operationReference = 456L;

    // when
    client
        .newDeleteResourceCommand(resourceKey)
        .operationReference(operationReference)
        .send()
        .join();

    // then
    final DeleteResourceRequest request =
        gatewayService.getLastRequest(DeleteResourceRequest.class);
    assertThat(request.getOperationReference()).isEqualTo(operationReference);
    assertThat(request.getDeleteHistory()).isFalse();
  }

  @Test
  public void shouldNotHaveNullResponse() {
    // given
    final long resourceKey = 123L;
    gatewayService.onDeleteResourceRequest(resourceKey, DUMMY_RESPONSE);
    final DeleteResourceCommandStep1 command = client.newDeleteResourceCommand(resourceKey);

    // when
    final DeleteResourceResponse response = command.send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResourceKey()).isEqualTo(String.valueOf(resourceKey));
    assertThat(response.getCreateBatchOperationResponse()).isNull();
  }

  @Test
  public void shouldNotHaveNullResponseWithDeleteHistoryFalse() {
    // given
    final long resourceKey = 123L;
    gatewayService.onDeleteResourceRequest(resourceKey, DUMMY_RESPONSE);
    final DeleteResourceCommandStep1 command =
        client.newDeleteResourceCommand(resourceKey).deleteHistory(false);

    // when
    final DeleteResourceResponse response = command.send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResourceKey()).isEqualTo(String.valueOf(resourceKey));
    assertThat(response.getCreateBatchOperationResponse()).isNull();
  }

  @Test
  public void shouldNotHaveNullResponseWithDeleteHistoryTrue() {
    // given
    final long resourceKey = 123L;
    gatewayService.onDeleteResourceRequest(resourceKey, DUMMY_RESPONSE_WITH_BATCH);
    final DeleteResourceCommandStep1 command =
        client.newDeleteResourceCommand(resourceKey).deleteHistory(true);

    // when
    final DeleteResourceResponse response = command.send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResourceKey()).isEqualTo(String.valueOf(resourceKey));
    assertThat(response.getCreateBatchOperationResponse()).isNotNull();
    assertThat(response.getCreateBatchOperationResponse().getBatchOperationKey()).isEqualTo("34");
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    final long resourceKey = 123L;
    gatewayService.errorOnRequest(
        RestGatewayPaths.getResourceDeletionUrl(resourceKey),
        () -> new ProblemDetail().title("Invalid request").status(400));

    // when / then
    assertThatThrownBy(() -> client.newDeleteResourceCommand(resourceKey).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Invalid request");
  }
}
