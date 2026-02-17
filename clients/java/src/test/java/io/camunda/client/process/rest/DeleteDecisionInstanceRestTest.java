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
package io.camunda.client.process.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.DeleteDecisionInstanceRequest;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import org.junit.jupiter.api.Test;

public class DeleteDecisionInstanceRestTest extends ClientRestTest {

  private static final long DECISION_INSTANCE_KEY = 123L;

  @Test
  public void shouldSendDeleteCommand() {
    // when
    client.newDeleteDecisionInstanceCommand(DECISION_INSTANCE_KEY).send().join();

    // then
    final DeleteDecisionInstanceRequest request =
        gatewayService.getLastRequest(DeleteDecisionInstanceRequest.class);
    assertThat(request).isNotNull();
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getDeleteDecisionInstanceUrl(DECISION_INSTANCE_KEY),
        () -> new ProblemDetail().title("Invalid request").status(400));

    assertThatThrownBy(
            () -> client.newDeleteDecisionInstanceCommand(DECISION_INSTANCE_KEY).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldSetOperationReference() {
    // given
    final int operationReference = 456;

    // when
    client
        .newDeleteDecisionInstanceCommand(DECISION_INSTANCE_KEY)
        .operationReference(operationReference)
        .execute();

    // then
    final DeleteDecisionInstanceRequest request =
        gatewayService.getLastRequest(DeleteDecisionInstanceRequest.class);
    assertThat(request.getOperationReference()).isEqualTo(operationReference);
  }
}
