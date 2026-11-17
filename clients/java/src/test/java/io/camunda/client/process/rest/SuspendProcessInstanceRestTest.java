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
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.SuspendProcessInstanceRequest;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import org.junit.jupiter.api.Test;

public class SuspendProcessInstanceRestTest extends ClientRestTest {

  private static final long PROCESS_INSTANCE_KEY = 123L;

  @Test
  public void shouldSendSuspendCommand() {
    // when
    client.newSuspendProcessInstanceCommand(PROCESS_INSTANCE_KEY).send().join();

    // then
    final SuspendProcessInstanceRequest request =
        gatewayService.getLastRequest(SuspendProcessInstanceRequest.class);
    assertThat(request).isNotNull();
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getSuspendProcessInstanceUrl(PROCESS_INSTANCE_KEY),
        () -> new ProblemDetail().title("Invalid state transition").status(409));

    // when / then
    assertThatThrownBy(
            () -> client.newSuspendProcessInstanceCommand(PROCESS_INSTANCE_KEY).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Invalid state transition");
  }

  @Test
  public void shouldSetOperationReference() {
    // given
    final int operationReference = 456;

    // when
    client
        .newSuspendProcessInstanceCommand(PROCESS_INSTANCE_KEY)
        .operationReference(operationReference)
        .execute();

    // then
    final SuspendProcessInstanceRequest request =
        gatewayService.getLastRequest(SuspendProcessInstanceRequest.class);
    assertThat(request.getOperationReference()).isEqualTo(operationReference);
  }
}
