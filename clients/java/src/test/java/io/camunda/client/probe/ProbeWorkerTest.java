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
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static io.camunda.client.util.StringUtil.repeat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.probe.ActivatedProbe;
import io.camunda.client.api.worker.probe.ProbeAnswer;
import io.camunda.client.api.worker.probe.ProbeExceptionHandler;
import io.camunda.client.api.worker.probe.ProbeWorker;
import io.camunda.client.api.worker.probe.ProbeWorkerBuilderStep1.ProbeWorkerBuilderStep2;
import io.camunda.client.util.ClientRestTest;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public final class ProbeWorkerTest extends ClientRestTest {

  private static final String ACTIVATION_URL = REST_API_PATH + "/probes/activation";
  private static final String SECRET = "s3cr3t-value";

  private WireMockRuntimeInfo wireMock;
  private ProbeWorker worker;
  private String awsProbeId;
  private String slackProbeId;
  private long nextProbeKey;

  @BeforeEach
  void setUp(final WireMockRuntimeInfo mockInfo) {
    wireMock = mockInfo;
    awsProbeId = "validate-configuration/aws-" + System.nanoTime();
    slackProbeId = "validate-configuration/slack-" + System.nanoTime();
    nextProbeKey = System.nanoTime();
  }

  @AfterEach
  void closeWorker() {
    if (worker != null) {
      worker.close();
    }
  }

  @Test
  void shouldPollForAllProbeIdsInOneRequestAndAnswerEachWithItsHandler() {
    // given
    final long awsKey = nextProbeKey++;
    final long slackKey = nextProbeKey++;
    activateOnce(probe(awsKey, awsProbeId, "{}", "[]"), probe(slackKey, slackProbeId, "{}", "[]"));
    final AtomicReference<ActivatedProbe> awsProbe = new AtomicReference<>();
    final AtomicReference<ActivatedProbe> slackProbe = new AtomicReference<>();

    // when
    worker =
        client
            .newProbeWorker()
            .probeId(
                awsProbeId,
                probe -> {
                  awsProbe.set(probe);
                  return ProbeAnswer.ok(Collections.singletonMap("handler", "aws"));
                })
            .probeId(
                slackProbeId,
                probe -> {
                  slackProbe.set(probe);
                  return ProbeAnswer.unsupported();
                })
            .pollInterval(Duration.ofMillis(10))
            .open();

    // then
    await()
        .untilAsserted(
            () -> {
              verify(
                  answerOf(awsKey)
                      .withRequestBody(
                          equalToJson("{\"outcome\":\"OK\",\"data\":{\"handler\":\"aws\"}}")));
              verify(
                  answerOf(slackKey).withRequestBody(equalToJson("{\"outcome\":\"UNSUPPORTED\"}")));
            });
    assertThat(awsProbe.get().getProbeId()).isEqualTo(awsProbeId);
    assertThat(slackProbe.get().getProbeId()).isEqualTo(slackProbeId);
    verify(
        postRequestedFor(urlPathEqualTo(ACTIVATION_URL))
            .withRequestBody(
                equalToJson(
                    "{\"probeIds\":[\"" + awsProbeId + "\",\"" + slackProbeId + "\"]}",
                    false,
                    true)));
  }

  @Test
  void shouldPollWithTheClientDefaults() {
    // given
    activateNothing();

    // when
    worker = client.newProbeWorker().probeId(awsProbeId, probe -> ProbeAnswer.unsupported()).open();

    // then
    await()
        .untilAsserted(
            () ->
                verify(
                    postRequestedFor(urlPathEqualTo(ACTIVATION_URL))
                        .withRequestBody(
                            equalToJson(
                                "{\"probeIds\":[\""
                                    + awsProbeId
                                    + "\"],\"worker\":\"default\",\"maxProbesToActivate\":32,"
                                    + "\"requestTimeout\":10000,\"tenantIds\":[\"<default>\"],"
                                    + "\"tenantFilter\":\"PROVIDED\"}"))));
  }

  @Test
  void shouldPollWithTheWorkerOptions() {
    // given
    activateNothing();

    // when
    worker =
        client
            .newProbeWorker()
            .probeId(awsProbeId, probe -> ProbeAnswer.unsupported())
            .name("connectors-saas")
            .tenantIds("tenant-a", "tenant-b")
            .maxProbesActive(8)
            .requestTimeout(Duration.ofSeconds(20))
            .open();

    // then
    await()
        .untilAsserted(
            () ->
                verify(
                    postRequestedFor(urlPathEqualTo(ACTIVATION_URL))
                        .withRequestBody(
                            equalToJson(
                                "{\"probeIds\":[\""
                                    + awsProbeId
                                    + "\"],\"worker\":\"connectors-saas\",\"maxProbesToActivate\":8,"
                                    + "\"requestTimeout\":20000,"
                                    + "\"tenantIds\":[\"tenant-a\",\"tenant-b\"],"
                                    + "\"tenantFilter\":\"PROVIDED\"}"))));
  }

  @Test
  void shouldPollWithoutTenantIdsForAssignedTenants() {
    // given
    activateNothing();

    // when
    worker =
        client
            .newProbeWorker()
            .probeId(awsProbeId, probe -> ProbeAnswer.unsupported())
            .tenantIds("tenant-a")
            .tenantFilter(TenantFilter.ASSIGNED)
            .open();

    // then
    await()
        .untilAsserted(
            () ->
                verify(
                    postRequestedFor(urlPathEqualTo(ACTIVATION_URL))
                        .withRequestBody(
                            equalToJson(
                                "{\"tenantIds\":[],\"tenantFilter\":\"ASSIGNED\"}", false, true))));
  }

  @Test
  void shouldBackOffAndKeepPollingOnAClusterWithoutProbes() {
    // given
    wireMock
        .getWireMock()
        .register(post(urlPathEqualTo(ACTIVATION_URL)).willReturn(problem(404, "NOT_FOUND")));
    final List<Long> previousDelays = new CopyOnWriteArrayList<>();
    final BackoffSupplier backoff =
        previousDelay -> {
          previousDelays.add(previousDelay);
          return previousDelay * 2;
        };

    // when
    worker =
        client
            .newProbeWorker()
            .probeId(awsProbeId, probe -> ProbeAnswer.unsupported())
            .pollInterval(Duration.ofMillis(5))
            .backoffSupplier(backoff)
            .open();

    // then
    await().untilAsserted(() -> assertThat(previousDelays).hasSizeGreaterThanOrEqualTo(4));
    assertThat(previousDelays.subList(0, 4)).containsExactly(5L, 10L, 20L, 40L);
    assertThat(worker.isOpen()).isTrue();
  }

  @Test
  void shouldAnswerErrorWithoutTheInjectedValues() {
    // given
    final long key = nextProbeKey++;
    activateOnce(
        probe(
            key,
            awsProbeId,
            "{\"region\":\"eu-west-1\",\"credentials\":{\"secretKey\":\"" + SECRET + "\"}}",
            "[\"/credentials/secretKey\"]"));

    // when
    worker =
        client
            .newProbeWorker()
            .probeId(
                awsProbeId,
                probe -> {
                  throw new IllegalStateException(
                      "AWS rejected " + SECRET + " in eu-west-1: " + repeat("x", 600));
                })
            .pollInterval(Duration.ofMillis(10))
            .open();

    // then
    final String expected =
        ("AWS rejected *** in eu-west-1: " + repeat("x", 600)).substring(0, 500);
    await()
        .untilAsserted(
            () ->
                verify(
                    answerOf(key)
                        .withRequestBody(
                            equalToJson(
                                "{\"outcome\":\"ERROR\",\"message\":\"" + expected + "\"}"))));
  }

  @Test
  void shouldAnswerErrorWithTheProvidedMessage() {
    // given
    final long key = nextProbeKey++;
    activateOnce(probe(key, awsProbeId, "{}", "[]"));

    // when
    worker =
        client
            .newProbeWorker()
            .probeId(
                awsProbeId,
                probe -> {
                  throw new IllegalStateException("failure");
                })
            .probeExceptionHandler(
                ProbeExceptionHandler.withErrorMessageProvider(
                    context -> "Failed: " + context.getException().getMessage()))
            .pollInterval(Duration.ofMillis(10))
            .open();

    // then
    await()
        .untilAsserted(
            () ->
                verify(
                    answerOf(key)
                        .withRequestBody(
                            equalToJson(
                                "{\"outcome\":\"ERROR\",\"message\":\"Failed: failure\"}"))));
  }

  @Test
  void shouldAnswerErrorWhenTheHandlerReturnsNoAnswer() {
    // given
    final long key = nextProbeKey++;
    activateOnce(probe(key, awsProbeId, "{}", "[]"));

    // when
    worker =
        client
            .newProbeWorker()
            .probeId(awsProbeId, probe -> null)
            .pollInterval(Duration.ofMillis(10))
            .open();

    // then
    await()
        .untilAsserted(
            () ->
                verify(
                    answerOf(key)
                        .withRequestBody(
                            equalToJson(
                                "{\"outcome\":\"ERROR\","
                                    + "\"message\":\"The probe handler returned no answer.\"}"))));
  }

  @Test
  void shouldSendTheAnswerAgainAfterA503() {
    // given
    final long key = nextProbeKey++;
    activateOnce(probe(key, awsProbeId, "{}", "[]"));
    answerFailsOnceWith(key, problem(503, "RESOURCE_EXHAUSTED"));

    // when
    worker = openUnsupportedWorker();

    // then
    await().untilAsserted(() -> assertThat(answerCount(key)).isEqualTo(2));
    await()
        .during(Duration.ofMillis(300))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(answerCount(key)).isEqualTo(2));
  }

  @Test
  void shouldSendTheAnswerAgainAfterANetworkError() {
    // given
    final long key = nextProbeKey++;
    activateOnce(probe(key, awsProbeId, "{}", "[]"));
    answerFailsOnceWith(key, aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

    // when
    worker = openUnsupportedWorker();

    // then
    await().untilAsserted(() -> assertThat(answerCount(key)).isEqualTo(2));
  }

  @Test
  void shouldAnswerErrorWhenTheAnswerIsTooLarge() {
    // given
    final long key = nextProbeKey++;
    activateOnce(probe(key, awsProbeId, "{}", "[]"));
    wireMock
        .getWireMock()
        .register(
            post(urlPathEqualTo(answerUrl(key)))
                .withRequestBody(matchingJsonPath("$.outcome", WireMock.equalTo("OK")))
                .willReturn(problem(413, "PAYLOAD_TOO_LARGE")));
    wireMock
        .getWireMock()
        .register(
            post(urlPathEqualTo(answerUrl(key)))
                .withRequestBody(matchingJsonPath("$.outcome", WireMock.equalTo("ERROR")))
                .willReturn(noContent()));

    // when
    worker =
        client
            .newProbeWorker()
            .probeId(awsProbeId, probe -> ProbeAnswer.ok(Collections.singletonMap("big", "x")))
            .pollInterval(Duration.ofMillis(10))
            .open();

    // then
    await()
        .untilAsserted(
            () ->
                verify(
                    answerOf(key)
                        .withRequestBody(
                            equalToJson(
                                "{\"outcome\":\"ERROR\","
                                    + "\"message\":\"The answer is too large.\"}"))));
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 403, 404, 409})
  void shouldNotSendTheAnswerAgainAfterAnAnswerTheClusterRefused(final int status) {
    // given
    final long key = nextProbeKey++;
    activateOnce(probe(key, awsProbeId, "{}", "[]"));
    wireMock
        .getWireMock()
        .register(post(urlPathEqualTo(answerUrl(key))).willReturn(problem(status, "REFUSED")));

    // when
    worker = openUnsupportedWorker();

    // then
    await().untilAsserted(() -> assertThat(answerCount(key)).isEqualTo(1));
    await()
        .during(Duration.ofMillis(300))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(answerCount(key)).isEqualTo(1));
  }

  @Test
  void shouldStopSendingTheAnswerAtTheDeadline() {
    // given
    final long key = nextProbeKey++;
    final long deadline = System.currentTimeMillis() + 500;
    activateOnce(probe(key, awsProbeId, "{}", "[]", deadline));
    wireMock
        .getWireMock()
        .register(
            post(urlPathEqualTo(answerUrl(key))).willReturn(problem(503, "RESOURCE_EXHAUSTED")));

    // when
    worker =
        client
            .newProbeWorker()
            .probeId(awsProbeId, probe -> ProbeAnswer.unsupported())
            .pollInterval(Duration.ofMillis(10))
            .backoffSupplier(previousDelay -> 50)
            .open();

    // then - retried while the deadline was ahead, and not once after it
    await().untilAsserted(() -> assertThat(answerCount(key)).isGreaterThan(1));
    await().until(() -> System.currentTimeMillis() > deadline + 100);
    final int answers = answerCount(key);
    await()
        .during(Duration.ofMillis(300))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(answerCount(key)).isEqualTo(answers));
  }

  @Test
  void shouldPrintTheProbeWithoutTheInjectedValues() {
    // given
    final long key = nextProbeKey++;
    activateOnce(probe(key, awsProbeId, "{\"secretKey\":\"" + SECRET + "\"}", "[\"/secretKey\"]"));
    final AtomicReference<ActivatedProbe> received = new AtomicReference<>();

    // when
    worker =
        client
            .newProbeWorker()
            .probeId(
                awsProbeId,
                probe -> {
                  received.set(probe);
                  return ProbeAnswer.unsupported();
                })
            .pollInterval(Duration.ofMillis(10))
            .open();

    // then
    await().untilAsserted(() -> assertThat(received.get()).isNotNull());
    final ActivatedProbe probe = received.get();
    assertThat(probe.toString()).doesNotContain(SECRET).contains("\"secretKey\":\"***\"");
    assertThat(probe.toJson()).contains(SECRET);
    assertThat(probe.getInputAsMap()).containsEntry("secretKey", SECRET);
    assertThat(probe.getSecretPointers()).containsExactly("/secretKey");
    assertThat(probe.getKey()).isEqualTo(key);
    assertThat(probe.getWorker()).isEqualTo("default");
    assertThat(probe.getTenantId()).isEqualTo("<default>");
    assertThat(probe.getPhysicalTenantId()).isEqualTo("default");
  }

  @Test
  void shouldStopPollingWhenClosed() {
    // given
    activateNothing();
    worker =
        client
            .newProbeWorker()
            .probeId(awsProbeId, probe -> ProbeAnswer.unsupported())
            .pollInterval(Duration.ofMillis(10))
            .open();
    await().untilAsserted(() -> assertThat(activationCount()).isPositive());

    // when
    worker.close();

    // then
    await().untilAsserted(() -> assertThat(worker.isClosed()).isTrue());
    final int polls = activationCount();
    await()
        .during(Duration.ofMillis(200))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(activationCount()).isEqualTo(polls));
  }

  @Test
  void shouldRejectTheSameProbeIdTwice() {
    // given
    final ProbeWorkerBuilderStep2 builder =
        client.newProbeWorker().probeId(awsProbeId, probe -> ProbeAnswer.unsupported());

    // when / then
    assertThatThrownBy(() -> builder.probeId(awsProbeId, probe -> ProbeAnswer.unsupported()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(awsProbeId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "*", "with space", "with\ttab", "with em-space"})
  void shouldRejectAnInvalidProbeId(final String probeId) {
    assertThatThrownBy(
            () -> client.newProbeWorker().probeId(probeId, probe -> ProbeAnswer.unsupported()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectAProbeIdLongerThan256Characters() {
    // given
    final String probeId = repeat("p", 257);

    // when / then
    assertThatThrownBy(
            () -> client.newProbeWorker().probeId(probeId, probe -> ProbeAnswer.unsupported()))
        .isInstanceOf(IllegalArgumentException.class);
    client.newProbeWorker().probeId(repeat("p", 256), probe -> ProbeAnswer.unsupported());
  }

  @Test
  void shouldRejectMoreThan100ProbeIds() {
    // given
    final ProbeWorkerBuilderStep2 builder =
        client.newProbeWorker().probeId("probe-0", probe -> ProbeAnswer.unsupported());
    for (int i = 1; i <= 100; i++) {
      builder.probeId("probe-" + i, probe -> ProbeAnswer.unsupported());
    }

    // when / then
    assertThatThrownBy(builder::open)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("100");
  }

  private ProbeWorker openUnsupportedWorker() {
    return client
        .newProbeWorker()
        .probeId(awsProbeId, probe -> ProbeAnswer.unsupported())
        .pollInterval(Duration.ofMillis(10))
        .backoffSupplier(previousDelay -> 10)
        .open();
  }

  private void activateOnce(final String... probes) {
    wireMock
        .getWireMock()
        .register(
            post(urlPathEqualTo(ACTIVATION_URL))
                .inScenario("activation")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson("{\"probes\":[" + String.join(",", probes) + "]}"))
                .willSetStateTo("collected"));
    wireMock
        .getWireMock()
        .register(
            post(urlPathEqualTo(ACTIVATION_URL))
                .inScenario("activation")
                .whenScenarioStateIs("collected")
                .willReturn(okJson("{\"probes\":[]}")));
  }

  private void activateNothing() {
    wireMock
        .getWireMock()
        .register(post(urlPathEqualTo(ACTIVATION_URL)).willReturn(okJson("{\"probes\":[]}")));
  }

  private void answerFailsOnceWith(final long key, final ResponseDefinitionBuilder failure) {
    wireMock
        .getWireMock()
        .register(
            post(urlPathEqualTo(answerUrl(key)))
                .inScenario("answer")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(failure)
                .willSetStateTo("failed"));
    wireMock
        .getWireMock()
        .register(
            post(urlPathEqualTo(answerUrl(key)))
                .inScenario("answer")
                .whenScenarioStateIs("failed")
                .willReturn(noContent()));
  }

  private int answerCount(final long key) {
    return wireMock.getWireMock().find(answerOf(key)).size();
  }

  private int activationCount() {
    return wireMock.getWireMock().find(postRequestedFor(urlPathEqualTo(ACTIVATION_URL))).size();
  }

  private void verify(final RequestPatternBuilder request) {
    wireMock.getWireMock().verifyThat(request);
  }

  private static RequestPatternBuilder answerOf(final long key) {
    return postRequestedFor(urlPathEqualTo(answerUrl(key)));
  }

  private static String answerUrl(final long key) {
    return REST_API_PATH + "/probes/" + key + "/answer";
  }

  private static ResponseDefinitionBuilder problem(final int status, final String title) {
    return aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/problem+json")
        .withBody(
            "{\"type\":\"about:blank\",\"title\":\""
                + title
                + "\",\"status\":"
                + status
                + ",\"detail\":\"detail\"}");
  }

  private static String probe(
      final long key, final String probeId, final String input, final String secretPointers) {
    return probe(key, probeId, input, secretPointers, System.currentTimeMillis() + 60_000);
  }

  private static String probe(
      final long key,
      final String probeId,
      final String input,
      final String secretPointers,
      final long deadline) {
    return "{\"probeKey\":\""
        + key
        + "\",\"probeId\":\""
        + probeId
        + "\",\"worker\":\"default\",\"deadline\":"
        + deadline
        + ",\"tenantId\":\"<default>\",\"physicalTenantId\":\"default\",\"input\":"
        + input
        + ",\"secretPointers\":"
        + secretPointers
        + "}";
  }
}
