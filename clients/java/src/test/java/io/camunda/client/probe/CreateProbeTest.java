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
package io.camunda.client.probe;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ProbeOutcome;
import io.camunda.client.api.response.ProbeResult;
import io.camunda.client.protocol.rest.ProbeCreationRequest;
import io.camunda.client.util.ClientRestTest;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class CreateProbeTest extends ClientRestTest {

  private static final String PROBES_URL = REST_API_PATH + "/probes";

  private WireMockRuntimeInfo wireMock;
  private String probeId;

  @BeforeEach
  void setUp(final WireMockRuntimeInfo mockInfo) {
    wireMock = mockInfo;
    probeId = "validate-configuration/" + UUID.randomUUID();
  }

  @Test
  void shouldSendProbe() {
    // given
    answerWith("{\"outcome\":\"UNSUPPORTED\",\"worker\":\"w\"}");
    final String input = "={region: \"eu-west-1\", accessKey: camunda.secrets.awsKey}";

    // when
    client.newCreateProbeCommand().probeId(probeId).input(input).send().join();

    // then
    assertThat(gatewayService.getLastRequest(ProbeCreationRequest.class))
        .isEqualTo(new ProbeCreationRequest().probeId(probeId).input(input).tenantId("<default>"));
  }

  @Test
  void shouldSendTenantIdAndRequestTimeout() {
    // given
    answerWith("{\"outcome\":\"UNSUPPORTED\",\"worker\":\"w\"}");

    // when
    client
        .newCreateProbeCommand()
        .probeId(probeId)
        .input("={}")
        .tenantId("tenant-a")
        .requestTimeout(Duration.ofSeconds(5))
        .send()
        .join();

    // then
    assertThat(gatewayService.getLastRequest(ProbeCreationRequest.class))
        .isEqualTo(
            new ProbeCreationRequest()
                .probeId(probeId)
                .input("={}")
                .tenantId("tenant-a")
                .requestTimeout(5_000L));
  }

  @Test
  void shouldSendMapInputAsContextOfLiteralValues() {
    // given
    answerWith("{\"outcome\":\"UNSUPPORTED\",\"worker\":\"w\"}");
    final Map<String, Object> input = new LinkedHashMap<>();
    input.put("region", "eu-west-1");
    input.put("url", "=camunda.secrets.notAReference");

    // when
    client.newCreateProbeCommand().probeId(probeId).input(input).send().join();

    // then
    assertThat(gatewayService.getLastRequest(ProbeCreationRequest.class).getInput())
        .isEqualTo("={\"region\":\"eu-west-1\",\"url\":\"=camunda.secrets.notAReference\"}");
  }

  @Test
  void shouldSendObjectInputAsContextOfLiteralValues() {
    // given
    answerWith("{\"outcome\":\"UNSUPPORTED\",\"worker\":\"w\"}");

    // when
    client.newCreateProbeCommand().probeId(probeId).input(new Region("eu-west-1")).send().join();

    // then
    assertThat(gatewayService.getLastRequest(ProbeCreationRequest.class).getInput())
        .isEqualTo("={\"region\":\"eu-west-1\"}");
  }

  @Test
  void shouldReturnOkAnswer() {
    // given
    answerWith("{\"outcome\":\"OK\",\"worker\":\"connectors\",\"data\":{\"status\":\"SUCCESS\"}}");

    // when
    final ProbeResult result =
        client.newCreateProbeCommand().probeId(probeId).input("={}").send().join();

    // then
    assertThat(result.getOutcome()).isEqualTo(ProbeOutcome.OK);
    assertThat(result.getWorker()).isEqualTo("connectors");
    assertThat(result.getData()).isEqualTo(Collections.singletonMap("status", "SUCCESS"));
    assertThat(result.getMessage()).isNull();
  }

  @Test
  void shouldReturnUnsupportedAnswer() {
    // given
    answerWith("{\"outcome\":\"UNSUPPORTED\",\"worker\":\"connectors\"}");

    // when
    final ProbeResult result =
        client.newCreateProbeCommand().probeId(probeId).input("={}").send().join();

    // then
    assertThat(result.getOutcome()).isEqualTo(ProbeOutcome.UNSUPPORTED);
    assertThat(result.getWorker()).isEqualTo("connectors");
    assertThat(result.getData()).isNull();
    assertThat(result.getMessage()).isNull();
  }

  @Test
  void shouldReturnErrorAnswer() {
    // given
    answerWith("{\"outcome\":\"ERROR\",\"worker\":\"connectors\",\"message\":\"failed\"}");

    // when
    final ProbeResult result =
        client.newCreateProbeCommand().probeId(probeId).input("={}").send().join();

    // then
    assertThat(result.getOutcome()).isEqualTo(ProbeOutcome.ERROR);
    assertThat(result.getWorker()).isEqualTo("connectors");
    assertThat(result.getData()).isNull();
    assertThat(result.getMessage()).isEqualTo("failed");
  }

  @Test
  void shouldRaiseProblemWhenNoWorkerAnswered() {
    // given
    wireMock
        .getWireMock()
        .register(
            post(urlPathEqualTo(PROBES_URL))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(
                            "{\"type\":\"about:blank\",\"title\":\"NO_WORKER\",\"status\":404,"
                                + "\"detail\":\"No worker collected the probe.\"}")));

    // when / then
    assertThatThrownBy(
            () -> client.newCreateProbeCommand().probeId(probeId).input("={}").send().join())
        .isInstanceOf(ProblemException.class)
        .satisfies(
            e -> {
              final ProblemException problem = (ProblemException) e;
              assertThat(problem.code()).isEqualTo(404);
              assertThat(problem.details().getTitle()).isEqualTo("NO_WORKER");
            });
  }

  @Test
  void shouldWaitLongerThanTheRequestTimeout() {
    // given - the cluster answers after the probe's deadline, as it does with 404 NO_WORKER
    wireMock
        .getWireMock()
        .register(
            post(urlPathEqualTo(PROBES_URL))
                .willReturn(
                    okJson("{\"outcome\":\"UNSUPPORTED\",\"worker\":\"w\"}").withFixedDelay(800)));

    // when
    final ProbeResult result =
        client
            .newCreateProbeCommand()
            .probeId(probeId)
            .input("={}")
            .requestTimeout(Duration.ofMillis(100))
            .send()
            .join();

    // then
    assertThat(result.getOutcome()).isEqualTo(ProbeOutcome.UNSUPPORTED);
  }

  @Test
  void shouldStopWaitingOneSecondAfterTheRequestTimeout() {
    // given
    wireMock
        .getWireMock()
        .register(
            post(urlPathEqualTo(PROBES_URL))
                .willReturn(
                    okJson("{\"outcome\":\"UNSUPPORTED\",\"worker\":\"w\"}")
                        .withFixedDelay(3_000)));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCreateProbeCommand()
                    .probeId(probeId)
                    .input("={}")
                    .requestTimeout(Duration.ofMillis(100))
                    .send()
                    .join())
        .hasRootCauseInstanceOf(java.net.SocketTimeoutException.class);
  }

  private void answerWith(final String body) {
    wireMock.getWireMock().register(post(urlPathEqualTo(PROBES_URL)).willReturn(okJson(body)));
  }

  private static final class Region {
    private final String region;

    private Region(final String region) {
      this.region = region;
    }

    public String getRegion() {
      return region;
    }
  }
}
