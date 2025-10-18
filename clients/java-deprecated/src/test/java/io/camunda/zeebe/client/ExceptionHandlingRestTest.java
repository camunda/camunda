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
package io.camunda.zeebe.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.protocol.rest.ProblemDetail;
import io.camunda.zeebe.client.util.ClientRestTest;
import io.camunda.zeebe.client.util.RestGatewayPaths;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public final class ExceptionHandlingRestTest extends ClientRestTest {

  @Test
  public void shouldProvideProblemExceptionOnFailedRequest() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getTopologyUrl(),
        () -> new ProblemDetail().title("Invalid request").status(400));

    // when / then
    assertThatThrownBy(() -> client.newTopologyRequest().useRest().send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldProvideProblemExceptionOnFailedRequestWithTimeout() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getTopologyUrl(),
        () -> new ProblemDetail().title("Invalid request").status(400));

    // when / then
    assertThatThrownBy(
            () -> client.newTopologyRequest().useRest().send().join(1L, TimeUnit.SECONDS))
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Invalid request");
  }

  // regression test for https://github.com/camunda/camunda/issues/39675
  @Test
  public void shouldIgnoreUnknownProperties() {
    // given / a response with an unknown property "kind" in a job
    gatewayService.onActivateJobsRequest(
        "{\"jobs\": [{ \"type\": \"type\", \"kind\": \"BPMN_ELEMENT\"}]}");

    // when / then
    final ActivateJobsResponse activateJobsResponse =
        client.newActivateJobsCommand().jobType("type").maxJobsToActivate(1).send().join();
    assertThat(activateJobsResponse).isNotNull();
    assertThat(activateJobsResponse.getJobs())
        .hasSize(1)
        .allSatisfy(job -> assertThat(job.getType()).isEqualTo("type"));
  }
}
