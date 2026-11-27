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
package io.camunda.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.BackpressureProfile;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.client.impl.CamundaClientImpl;
import io.camunda.client.impl.backpressure.BackpressureManager;
import io.camunda.client.impl.backpressure.BackpressureManager.Permit;
import io.camunda.client.impl.backpressure.BackpressureManager.Severity;
import io.camunda.client.util.ClientRestTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the client against a stubbed gateway that rejects requests with the signals of backpressure,
 * and checks what the client does with them.
 */
public final class BackpressureRestTest extends ClientRestTest {

  private static final String PUBLISH_PATH = "/v2/messages/publication";
  private static final String PUBLISH_RESULT =
      "{\"messageKey\":\"123\",\"tenantId\":\"<default>\"}";

  private static ResponseDefinitionBuilder problem(
      final int status, final String title, final String detail) {
    return aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/problem+json")
        .withBody(
            String.format(
                "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}",
                title, status, detail));
  }

  /** How the gateway answers a request that the broker rejected because of backpressure. */
  private static ResponseDefinitionBuilder resourceExhausted() {
    return problem(503, "RESOURCE_EXHAUSTED", "Partition 1 is currently rejecting requests");
  }

  private static ResponseDefinitionBuilder published() {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(PUBLISH_RESULT);
  }

  /** Answers the requests to the path in turn, the last answer stays. */
  private static void stubSequence(
      final String scenario, final String path, final ResponseDefinitionBuilder... responses) {
    for (int i = 0; i < responses.length; i++) {
      final String state = i == 0 ? Scenario.STARTED : "answer-" + i;
      final ScenarioMappingBuilder stub =
          post(urlPathEqualTo(path))
              .inScenario(scenario)
              .whenScenarioStateIs(state)
              .willReturn(responses[i]);
      stubFor(i < responses.length - 1 ? stub.willSetStateTo("answer-" + (i + 1)) : stub);
    }
  }

  /** Backpressure handling is opt-in, so the client of the base class is replaced. */
  @BeforeEach
  void optIn() {
    client.close();
    client = newClient(BackpressureProfile.BALANCED);
  }

  private CamundaFuture<?> publish(final CamundaClient c) {
    return c.newPublishMessageCommand().messageName("order").correlationKey("1").send();
  }

  private BackpressureManager limiter() {
    return ((CamundaClientImpl) client).backpressure().manager();
  }

  private CamundaClient newClient(final BackpressureProfile profile) {
    return CamundaClient.newClientBuilder()
        .preferRestOverGrpc(true)
        .restAddress(client.getConfiguration().getRestAddress())
        .backpressureProfile(profile)
        .build();
  }

  @Test
  void shouldNotHandleBackpressureByDefault() {
    // given: a client that was not told to handle backpressure
    stubFor(post(urlPathEqualTo(PUBLISH_PATH)).willReturn(resourceExhaustedAs500()));
    try (final CamundaClient byDefault =
        CamundaClient.newClientBuilder()
            .preferRestOverGrpc(true)
            .restAddress(client.getConfiguration().getRestAddress())
            .build()) {
      // when / then: nothing is retried
      assertThat(byDefault.getConfiguration().getBackpressureProfile())
          .isEqualTo(BackpressureProfile.DISABLED);
      assertThat(((CamundaClientImpl) byDefault).backpressure().isEnabled()).isFalse();
      assertThatThrownBy(() -> publish(byDefault).join()).isInstanceOf(ProblemException.class);
      verify(exactly(1), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
    }
  }

  @Test
  void shouldRetryAfterTheBrokerRejectedBecauseOfBackpressure() {
    // given: the first request is rejected, the second one goes through
    stubSequence("retry", PUBLISH_PATH, resourceExhausted(), published());

    // when
    final PublishMessageResponse response =
        client.newPublishMessageCommand().messageName("order").correlationKey("1").send().join();

    // then
    assertThat(response.getMessageKey()).isEqualTo(123L);
    verify(exactly(2), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
  }

  @Test
  void shouldRetryOnTooManyRequests() {
    // given
    stubSequence("429", PUBLISH_PATH, aResponse().withStatus(429), published());

    // when
    publish(client).join();

    // then
    verify(exactly(2), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
  }

  @Test
  void shouldRetryOnInternalErrorThatReportsResourceExhausted() {
    // given
    stubSequence(
        "500",
        PUBLISH_PATH,
        problem(500, "Internal Server Error", "RESOURCE_EXHAUSTED: partition is busy"),
        published());

    // when
    publish(client).join();

    // then
    verify(exactly(2), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
  }

  @Test
  void shouldNotRetryOtherServiceUnavailableResponses() {
    // given: a 503 that is not backpressure, e.g. the timeout of the gateway, which fires after the
    // command was started; httpclient5 used to send it again, which started it a second time
    stubFor(post(urlPathEqualTo(PUBLISH_PATH)).willReturn(problem(503, "UNAVAILABLE", "timeout")));

    // when / then
    assertThatThrownBy(() -> publish(client).join()).isInstanceOf(ProblemException.class);
    verify(exactly(1), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
    // and: such a response is no sign of load, so it does not tighten the limit
    assertThat(limiter().state().consecutive()).isZero();
    assertThat(limiter().state().permitsMax()).isNull();
  }

  @Test
  void shouldNotRetryAServiceUnavailableResponseWithoutBody() {
    // given
    stubFor(post(urlPathEqualTo(PUBLISH_PATH)).willReturn(aResponse().withStatus(503)));

    // when / then
    assertThatThrownBy(() -> publish(client).join()).isInstanceOf(ClientHttpException.class);
    verify(exactly(1), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
  }

  @Test
  void shouldGiveUpAfterThreeAttemptsAndFailWithTheLastRejection() {
    // given
    stubFor(post(urlPathEqualTo(PUBLISH_PATH)).willReturn(resourceExhausted()));

    // when / then
    assertThatThrownBy(() -> publish(client).join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("RESOURCE_EXHAUSTED")
        .satisfies(e -> assertThat(((ProblemException) e).code()).isEqualTo(503));
    // and: three attempts in total, so the HTTP client did not retry on top
    verify(exactly(3), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
  }

  @Test
  void shouldTightenTheLimitForEveryRejectionIncludingTheLastOne() {
    // given
    stubFor(post(urlPathEqualTo(PUBLISH_PATH)).willReturn(resourceExhausted()));

    // when
    assertThatThrownBy(() -> publish(client).join()).isInstanceOf(ProblemException.class);

    // then
    assertThat(limiter().state().consecutive()).isEqualTo(3);
    assertThat(limiter().state().severity()).isEqualTo(Severity.SEVERE);
    // 16 -> 12 -> 9 -> 5
    assertThat(limiter().state().permitsMax()).isEqualTo(5);
    assertThat(limiter().state().permitsCurrent()).isZero();
  }

  @Test
  void shouldNotRetryFailuresThatAreNotBackpressure() {
    // given
    stubFor(post(urlPathEqualTo(PUBLISH_PATH)).willReturn(problem(500, "INTERNAL", "boom")));

    // when / then
    assertThatThrownBy(() -> publish(client).join()).isInstanceOf(ProblemException.class);
    verify(exactly(1), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
  }

  @Test
  void shouldNotRetryRejectionsOfTheRequestItself() {
    // given
    stubFor(post(urlPathEqualTo(PUBLISH_PATH)).willReturn(problem(409, "ALREADY_EXISTS", "dup")));

    // when / then
    assertThatThrownBy(() -> publish(client).join()).isInstanceOf(ProblemException.class);
    verify(exactly(1), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
  }

  @Test
  void shouldNotRetryWhenTheConnectionBreaksWhileTheRequestIsInFlight() {
    // given: the command may or may not have been processed, so it must not be sent again
    stubFor(
        post(urlPathEqualTo(PUBLISH_PATH))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    // when / then
    assertThatThrownBy(() -> publish(client).join()).isInstanceOf(ClientException.class);
    verify(exactly(1), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
  }

  @Test
  void shouldRetryWhenTheGatewayAsksToRetryImmediately() {
    // given: how long to wait is covered by the unit tests, which control the time
    stubSequence(
        "retry-after",
        PUBLISH_PATH,
        aResponse().withStatus(429).withHeader("Retry-After", "0"),
        published());

    // when
    publish(client).join();

    // then
    verify(exactly(2), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
  }

  @Test
  void shouldNotRetryWhenTheRetryWouldOutliveTheRequestTimeout() {
    // given: the gateway asks to wait longer than the request may take
    stubFor(
        post(urlPathEqualTo(PUBLISH_PATH))
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));
    final long start = System.nanoTime();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newPublishMessageCommand()
                    .messageName("order")
                    .correlationKey("1")
                    .requestTimeout(Duration.ofSeconds(2))
                    .send()
                    .join())
        .isInstanceOf(ClientException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    verify(exactly(1), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
  }

  @Test
  void shouldRetryTheCompletionOfAJobButNeverHoldItBackForTheLimit() {
    // given: the limit is reached and used up
    final BackpressureManager limiter = limiter();
    for (int i = 0; i < 6; i++) {
      limiter.recordBackpressure();
    }
    final Permit held = limiter.acquire().join();
    assertThat(limiter.state().permitsMax()).isEqualTo(1);
    // a command that starts new work has to wait
    stubFor(post(urlPathEqualTo(PUBLISH_PATH)).willReturn(published()));
    final CamundaFuture<?> waiting = publish(client);
    await()
        .during(Duration.ofMillis(300))
        .atMost(Duration.ofSeconds(5))
        .until(() -> !waiting.isDone());
    verify(exactly(0), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));

    stubSequence(
        "complete",
        "/v2/jobs/1/completion",
        aResponse().withStatus(429),
        aResponse().withStatus(204));
    stubFor(post(urlPathEqualTo("/v2/jobs/2/failure")).willReturn(aResponse().withStatus(204)));
    stubFor(post(urlPathEqualTo("/v2/jobs/3/error")).willReturn(aResponse().withStatus(204)));
    stubFor(
        post(urlPathEqualTo("/v2/user-tasks/4/completion"))
            .willReturn(aResponse().withStatus(204)));

    // when / then: finishing work goes through, even though the limit is used up
    client.newCompleteCommand(1).send().join(5, TimeUnit.SECONDS);
    client.newFailCommand(2).retries(0).send().join(5, TimeUnit.SECONDS);
    client.newThrowErrorCommand(3).errorCode("x").send().join(5, TimeUnit.SECONDS);
    client.newCompleteUserTaskCommand(4).send().join(5, TimeUnit.SECONDS);
    // and the completion was retried after the rejection
    verify(exactly(2), postRequestedFor(urlPathEqualTo("/v2/jobs/1/completion")));

    held.release();
    waiting.join(5, TimeUnit.SECONDS);
  }

  @Test
  void shouldNeverHoldBackRequestsThatOnlyRead() {
    // given: the limit is reached and used up
    final BackpressureManager limiter = limiter();
    for (int i = 0; i < 6; i++) {
      limiter.recordBackpressure();
    }
    limiter.acquire().join();
    stubFor(
        post(urlPathEqualTo("/v2/process-instances/search"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"items\":[],\"page\":{\"totalItems\":0}}")));
    stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/v2/topology"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"brokers\":[],\"clusterSize\":1,\"partitionsCount\":1,\"replicationFactor\":1,\"gatewayVersion\":\"8.9.0\"}")));

    // when / then
    client.newProcessInstanceSearchRequest().send().join(5, TimeUnit.SECONDS);
    client.newTopologyRequest().useRest().send().join(5, TimeUnit.SECONDS);
    verify(exactly(1), getRequestedFor(urlPathEqualTo("/v2/topology")));
  }

  @Test
  void shouldNotRetryNorDelayJobActivation() {
    // given: the job worker backs off between polls on its own
    stubFor(post(urlPathEqualTo("/v2/jobs/activation")).willReturn(resourceExhausted()));

    // when / then
    assertThatThrownBy(
            () ->
                client.newActivateJobsCommand().jobType("work").maxJobsToActivate(1).send().join())
        .isInstanceOf(ProblemException.class);
    verify(exactly(1), postRequestedFor(urlPathEqualTo("/v2/jobs/activation")));
    assertThat(limiter().state().consecutive()).isZero();
  }

  @Test
  void shouldWithdrawACancelledRequestFromTheQueue() {
    // given
    final BackpressureManager limiter = limiter();
    for (int i = 0; i < 6; i++) {
      limiter.recordBackpressure();
    }
    final Permit held = limiter.acquire().join();
    stubFor(post(urlPathEqualTo(PUBLISH_PATH)).willReturn(published()));
    final CamundaFuture<?> waiting = publish(client);
    await().atMost(Duration.ofSeconds(5)).until(() -> limiter.state().waiters() == 1);

    // when
    waiting.cancel(true);
    held.release();

    // then
    await().atMost(Duration.ofSeconds(5)).until(() -> limiter.state().waiters() == 0);
    assertThat(limiter.state().permitsCurrent()).isZero();
    verify(exactly(0), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
  }

  private static ResponseDefinitionBuilder documentCreated() {
    return aResponse()
        .withStatus(201)
        .withHeader("Content-Type", "application/json")
        .withBody(
            "{\"camunda.document.type\":\"camunda\",\"documentId\":\"doc-1\","
                + "\"storeId\":\"in-memory\",\"contentHash\":\"abc\","
                + "\"metadata\":{\"contentType\":\"text/plain\",\"fileName\":\"a.txt\"}}");
  }

  private static void verifyDocumentUploads(final int count, final String content) {
    final List<LoggedRequest> uploads = findAll(postRequestedFor(urlPathEqualTo("/v2/documents")));
    assertThat(uploads).hasSize(count);
    assertThat(uploads)
        .allSatisfy(upload -> assertThat(upload.getBodyAsString()).contains(content));
  }

  @Test
  void shouldUploadTheWholeContentOfADocumentAgainWhenTheUploadIsRetried() {
    // given: the content comes from a stream that can be read only once
    final String content = "the content of the document";
    stubSequence("document", "/v2/documents", resourceExhausted(), documentCreated());

    // when
    client
        .newCreateDocumentCommand()
        .content(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
        .fileName("a.txt")
        .send()
        .join();

    // then
    verifyDocumentUploads(2, content);
  }

  @Test
  void shouldUploadTheWholeContentOfADocumentThatWaitedForItsTurn() {
    // given: the limit is used up, and the stream is closed once the command is sent
    final BackpressureManager limiter = limiter();
    for (int i = 0; i < 6; i++) {
      limiter.recordBackpressure();
    }
    final Permit held = limiter.acquire().join();
    final String content = "the content of the document";
    stubFor(post(urlPathEqualTo("/v2/documents")).willReturn(documentCreated()));
    final CamundaFuture<?> waiting =
        client
            .newCreateDocumentCommand()
            .content(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
            .fileName("a.txt")
            .send();
    await().atMost(Duration.ofSeconds(5)).until(() -> limiter.state().waiters() == 1);

    // when
    held.release();
    waiting.join(5, TimeUnit.SECONDS);

    // then
    verifyDocumentUploads(1, content);
  }

  @Test
  void shouldRestoreTheOldBehaviorWhenDisabled() {
    // given
    stubFor(
        post(urlPathEqualTo(PUBLISH_PATH))
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0")));

    try (final CamundaClient disabled = newClient(BackpressureProfile.DISABLED)) {
      // when / then: the HTTP client retries 429 once, and that is all
      assertThatThrownBy(() -> publish(disabled).join()).isInstanceOf(ClientHttpException.class);
      verify(exactly(2), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
      assertThat(((CamundaClientImpl) disabled).backpressure().isEnabled()).isFalse();
    }
  }

  @Test
  void shouldNotRetryBackpressureThatIsReportedAsAnInternalErrorWhenDisabled() {
    // given
    stubFor(post(urlPathEqualTo(PUBLISH_PATH)).willReturn(resourceExhaustedAs500()));

    try (final CamundaClient disabled = newClient(BackpressureProfile.DISABLED)) {
      // when / then
      assertThatThrownBy(() -> publish(disabled).join()).isInstanceOf(ProblemException.class);
      verify(exactly(1), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
    }
  }

  @Test
  void shouldRetryButNotLimitWithTheLegacyProfile() {
    // given
    stubFor(post(urlPathEqualTo(PUBLISH_PATH)).willReturn(resourceExhausted()));

    try (final CamundaClient legacy = newClient(BackpressureProfile.LEGACY)) {
      // when / then
      assertThatThrownBy(() -> publish(legacy).join()).isInstanceOf(ProblemException.class);
      verify(exactly(3), postRequestedFor(urlPathEqualTo(PUBLISH_PATH)));
      assertThat(((CamundaClientImpl) legacy).backpressure().manager().state().permitsMax())
          .isNull();
    }
  }

  private static ResponseDefinitionBuilder resourceExhaustedAs500() {
    return problem(500, "INTERNAL", "RESOURCE_EXHAUSTED");
  }
}
