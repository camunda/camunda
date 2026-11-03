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

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.client.api.worker.probe.ActivatedProbe;
import io.camunda.client.api.worker.probe.ProbeAnswer;
import io.camunda.client.api.worker.probe.ProbeWorker;
import io.camunda.client.api.worker.probe.ProbeWorkerBuilderStep1.ProbeWorkerBuilderStep2;
import io.camunda.client.util.ClientRestTest;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ProbeWorkerTest extends ClientRestTest {

  private static final String ACTIVATION_URL = REST_API_PATH + "/probes/activation";
  private static final String AWS = "validate-configuration/io.camunda:aws-credential:1";
  private static final String SLACK = "validate-configuration/io.camunda:slack:1";

  private WireMockRuntimeInfo wireMock;
  private ProbeWorker worker;

  @BeforeEach
  void captureWireMock(final WireMockRuntimeInfo mockInfo) {
    wireMock = mockInfo;
  }

  @AfterEach
  void closeWorker() {
    if (worker != null) {
      worker.close();
    }
  }

  @Test
  void shouldPollForAllProbeIdsAtOnceAndAnswerEachWithItsHandler() {
    // given - the first poll collects a probe of each probe ID, later polls none
    wireMock
        .getWireMock()
        .register(
            post(urlPathEqualTo(ACTIVATION_URL))
                .inScenario("activation")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(
                    okJson(
                        "{\"probes\":["
                            + activatedProbe(1, AWS)
                            + ","
                            + activatedProbe(2, SLACK)
                            + "]}"))
                .willSetStateTo("collected"));
    wireMock
        .getWireMock()
        .register(
            post(urlPathEqualTo(ACTIVATION_URL))
                .inScenario("activation")
                .whenScenarioStateIs("collected")
                .willReturn(okJson("{\"probes\":[]}")));
    final AtomicReference<ActivatedProbe> awsProbe = new AtomicReference<>();
    final AtomicReference<ActivatedProbe> slackProbe = new AtomicReference<>();

    // when
    worker =
        client
            .newProbeWorker()
            .probeId(
                AWS,
                probe -> {
                  awsProbe.set(probe);
                  return ProbeAnswer.ok(Collections.singletonMap("handler", "aws"));
                })
            .probeId(
                SLACK,
                probe -> {
                  slackProbe.set(probe);
                  return ProbeAnswer.unsupported();
                })
            .pollInterval(Duration.ofMillis(10))
            .open();

    // then
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(awsProbe.get()).isNotNull();
              assertThat(slackProbe.get()).isNotNull();
              wireMock
                  .getWireMock()
                  .verifyThat(
                      postRequestedFor(urlPathEqualTo(REST_API_PATH + "/probes/1/answer"))
                          .withRequestBody(
                              equalToJson("{\"outcome\":\"OK\",\"data\":{\"handler\":\"aws\"}}")));
              wireMock
                  .getWireMock()
                  .verifyThat(
                      postRequestedFor(urlPathEqualTo(REST_API_PATH + "/probes/2/answer"))
                          .withRequestBody(equalToJson("{\"outcome\":\"UNSUPPORTED\"}")));
            });
    assertThat(awsProbe.get().getProbeId()).isEqualTo(AWS);
    assertThat(slackProbe.get().getProbeId()).isEqualTo(SLACK);
    wireMock
        .getWireMock()
        .verifyThat(
            postRequestedFor(urlPathEqualTo(ACTIVATION_URL))
                .withRequestBody(
                    equalToJson(
                        "{\"probeIds\":[\"" + AWS + "\",\"" + SLACK + "\"]}", false, true)));
  }

  @Test
  void shouldRejectTheSameProbeIdTwice() {
    // given
    final ProbeWorkerBuilderStep2 builder =
        client.newProbeWorker().probeId(AWS, probe -> ProbeAnswer.unsupported());

    // when / then
    assertThatThrownBy(() -> builder.probeId(AWS, probe -> ProbeAnswer.unsupported()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(AWS);
  }

  private static String activatedProbe(final long probeKey, final String probeId) {
    return "{\"probeKey\":\""
        + probeKey
        + "\",\"probeId\":\""
        + probeId
        + "\",\"worker\":\"default\",\"deadline\":1,\"tenantId\":\"<default>\","
        + "\"physicalTenantId\":\"default\",\"input\":{}}";
  }
}
