/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.service.ProbeServices;
import io.camunda.service.ProbeServices.ProbeActivationRequest;
import io.camunda.service.ProbeServices.ProbeAnswerRequest;
import io.camunda.service.ProbeServices.ProbeCreateRequest;
import io.camunda.service.ProbeServices.ProbeResult;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.impl.job.ResponseObserver;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@WebMvcTest(ProbeController.class)
class ProbeControllerTest extends RestControllerTest {

  private static final String PROBE_ID = "validate-configuration/io.camunda:aws-credential:1";
  // the physical tenant filter is not part of this test slice: the prefixed route resolves to the
  // default physical tenant, as the unprefixed one does
  private static final String PREFIXED = "/physical-tenants/default/v2";
  private static final long PROBE_KEY = 2251799813685464L;

  @MockitoBean ProbeServices probeServices;
  @MockitoBean ServiceRegistry serviceRegistry;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;

  @BeforeEach
  void setUp() {
    Mockito.doReturn(probeServices).when(serviceRegistry).probeServices(any());
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  // ---------------------------------------------------------------------------------------------
  // createProbe
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {"/v2", PREFIXED})
  void shouldAnswerWithTheEnvelopeOfAnOkAnswer(final String prefix) {
    // given
    whenProbeEnds(
        answered(ProbeOutcome.OK)
            .setData(buffer("{\"status\": \"FAILURE\", \"code\": \"UNAUTHORIZED\"}")));

    // when
    final var response = createProbe(prefix, createBody());

    // then
    response
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "outcome": "OK",
              "worker": "connectors-runtime",
              "data": {"status": "FAILURE", "code": "UNAUTHORIZED"}
            }""",
            JsonCompareMode.STRICT);
    verify(serviceRegistry).probeServices(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldAnswerWithTheEnvelopeOfAnUnsupportedAnswer() {
    // given
    whenProbeEnds(answered(ProbeOutcome.UNSUPPORTED));

    // when / then
    createProbe("/v2", createBody())
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"outcome": "UNSUPPORTED", "worker": "connectors-runtime"}""",
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldAnswerWithTheEnvelopeOfAnErrorAnswer() {
    // given
    whenProbeEnds(answered(ProbeOutcome.ERROR).setMessage("AWS is unreachable."));

    // when / then
    createProbe("/v2", createBody())
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"outcome": "ERROR", "worker": "connectors-runtime", "message": "AWS is unreachable."}""",
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldSendTheProbeToTheService() {
    // given
    whenProbeEnds(answered(ProbeOutcome.UNSUPPORTED));
    final var captor = ArgumentCaptor.forClass(ProbeCreateRequest.class);

    // when
    createProbe(
            "/v2",
            """
            {"probeId": "%s", "input": "={region: \\"eu-west-1\\"}", "requestTimeout": 10000}"""
                .formatted(PROBE_ID))
        .expectStatus()
        .isOk();

    // then - without multi-tenancy, the probe belongs to the default tenant
    verify(probeServices).createProbe(captor.capture(), any());
    assertThat(captor.getValue())
        .isEqualTo(
            new ProbeCreateRequest(PROBE_ID, "={region: \"eu-west-1\"}", "<default>", 10_000L));
  }

  @Test
  void shouldAnswerNoWorkerWhenNoWorkerCollectedTheProbe() {
    // given - AT19
    whenProbeEnds(
        ended(
            ProbeEndReason.NO_WORKER,
            "No worker for '%s' collected the probe.".formatted(PROBE_ID)));

    // when / then
    expectProblem(
        createProbe("/v2", createBody()),
        404,
        "NO_WORKER",
        "No worker for '%s' collected the probe.".formatted(PROBE_ID));
  }

  @Test
  void shouldAnswerSecretResolutionErrorWhenTheSecretsCouldNotBeResolved() {
    // given
    whenProbeEnds(
        ended(ProbeEndReason.SECRET_RESOLUTION_ERROR, "Failed to resolve secret 'awsKey'."));

    // when / then
    expectProblem(
        createProbe("/v2", createBody()),
        400,
        "SECRET_RESOLUTION_ERROR",
        "Failed to resolve secret 'awsKey'.");
  }

  @Test
  void shouldAnswerWorkerDidNotAnswerWhenTheWorkerWasTooSlow() {
    // given - AT20
    whenProbeEnds(
        ended(ProbeEndReason.WORKER_DID_NOT_ANSWER, "The worker did not answer within 10000 ms."));

    // when / then
    expectProblem(
        createProbe("/v2", createBody()),
        504,
        "WORKER_DID_NOT_ANSWER",
        "The worker did not answer within 10000 ms.");
  }

  @Test
  void shouldAnswerSecretResolutionTimeoutWhenTheSecretsWereNotResolvedInTime() {
    // given
    whenProbeEnds(
        ended(
            ProbeEndReason.SECRET_RESOLUTION_TIMEOUT,
            "The secrets of the probe were not resolved within 10000 ms."));

    // when / then
    expectProblem(
        createProbe("/v2", createBody()),
        504,
        "SECRET_RESOLUTION_TIMEOUT",
        "The secrets of the probe were not resolved within 10000 ms.");
  }

  @Test
  void shouldAnswerDeadlineExceededWhenTheGatewayTimedOut() {
    // given
    when(probeServices.createProbe(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("request timed out", Status.DEADLINE_EXCEEDED)));

    // when / then
    expectProblem(createProbe("/v2", createBody()), 504, "DEADLINE_EXCEEDED", "request timed out");
  }

  @Test
  void shouldAnswerPayloadTooLargeWhenTheProbeDoesNotFitInALogBatch() {
    // given - AT23
    when(probeServices.createProbe(any(), any()))
        .thenReturn(CompletableFuture.failedFuture(tooLarge(ProbeIntent.CREATE)));

    // when / then
    expectProblem(
        createProbe("/v2", createBody()),
        413,
        "PAYLOAD_TOO_LARGE",
        "The probe is too large to be written to the log. Shorten its input, then retry.");
  }

  @Test
  void shouldKeepInternalForAnInternalErrorWithoutTheBatchSizeRejection() {
    // given
    when(probeServices.createProbe(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(new ServiceException("unexpected", Status.INTERNAL)));

    // when / then
    expectProblem(createProbe("/v2", createBody()), 500, "INTERNAL", "unexpected");
  }

  @Test
  void shouldMapTheServiceErrorsOfAProbe() {
    // given
    when(probeServices.createProbe(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(new ServiceException("not allowed", Status.FORBIDDEN)),
            CompletableFuture.failedFuture(
                new ServiceException("backpressure", Status.RESOURCE_EXHAUSTED)),
            CompletableFuture.failedFuture(
                new ServiceException("not a context", Status.INVALID_ARGUMENT)));

    // when / then
    expectProblem(createProbe("/v2", createBody()), 403, "FORBIDDEN", "not allowed");
    expectProblem(createProbe("/v2", createBody()), 503, "RESOURCE_EXHAUSTED", "backpressure");
    expectProblem(createProbe("/v2", createBody()), 400, "INVALID_ARGUMENT", "not a context");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"input\": \"={}\"}",
        "{\"probeId\": \"\", \"input\": \"={}\"}",
        "{\"probeId\": \"has whitespace\", \"input\": \"={}\"}",
        "{\"probeId\": \"*\", \"input\": \"={}\"}",
        "{\"probeId\": \"a\"}",
        "{\"probeId\": \"a\", \"input\": \"  \"}",
        "{\"probeId\": \"a\", \"input\": \"={}\", \"requestTimeout\": -1}",
        "{\"probeId\": \"a\", \"input\": \"={}\", \"requestTimeout\": 30001}"
      })
  void shouldRejectAnInvalidProbeBeforeTheEngine(final String body) {
    // when / then
    createProbe("/v2", body)
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("INVALID_ARGUMENT");
    verifyNoInteractions(probeServices);
  }

  @Test
  void shouldRejectAProbeWithAnUnknownField() {
    // when / then - the body is not read: the global handler answers, as for every endpoint
    createProbe("/v2", "{\"probeId\": \"a\", \"input\": \"={}\", \"unknown\": true}")
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(probeServices);
  }

  @Test
  void shouldRejectAPollWithTheFieldsOfAJobPoll() {
    // when / then - a probe's deadline is set by its client, and its input is always complete
    activateProbes("/v2", "{\"probeIds\": [\"a\"], \"maxProbesToActivate\": 1, \"timeout\": 1}")
        .expectStatus()
        .isBadRequest();
    activateProbes(
            "/v2",
            "{\"probeIds\": [\"a\"], \"maxProbesToActivate\": 1, \"fetchVariable\": [\"a\"]}")
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(probeServices);
  }

  @Test
  void shouldRejectAProbeIdOfMoreThan256Characters() {
    // when / then
    createProbe("/v2", "{\"probeId\": \"%s\", \"input\": \"={}\"}".formatted("a".repeat(257)))
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(probeServices);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 30_000})
  void shouldAcceptTheBoundsOfTheRequestTimeout(final long requestTimeout) {
    // given
    whenProbeEnds(answered(ProbeOutcome.UNSUPPORTED));

    // when / then
    createProbe(
            "/v2",
            "{\"probeId\": \"%s\", \"input\": \"={}\", \"requestTimeout\": %d}"
                .formatted("a".repeat(256), requestTimeout))
        .expectStatus()
        .isOk();
  }

  // ---------------------------------------------------------------------------------------------
  // activateProbes
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {"/v2", PREFIXED})
  void shouldAnswerAPollWithTheCollectedProbes(final String prefix) {
    // given
    final var batch =
        new ProbeBatchRecord()
            .addProbe(
                PROBE_KEY,
                new ProbeRecord()
                    .setProbeId(PROBE_ID)
                    .setTenantId("<default>")
                    .setWorker("connectors-runtime")
                    .setDeadline(1794232249291L)
                    .setInput(buffer("{\"accessKey\": \"value\", \"region\": \"eu-west-1\"}"))
                    .addSecretReference("store", "camunda.secrets.awsKey", "/accessKey")
                    .addSecretReference("store", "camunda.secrets.awsKey", "/accessKey"));
    whenPollIsAnswered(batch);

    // when / then - the pointers are distinct; the secret's name is never exposed
    activateProbes(prefix, activationBody())
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "probes": [
                {
                  "probeKey": "2251799813685464",
                  "probeId": "%s",
                  "worker": "connectors-runtime",
                  "deadline": 1794232249291,
                  "tenantId": "<default>",
                  "physicalTenantId": "%s",
                  "input": {"accessKey": "value", "region": "eu-west-1"},
                  "secretPointers": ["/accessKey"]
                }
              ]
            }"""
                .formatted(PROBE_ID, DEFAULT_PHYSICAL_TENANT_ID),
            JsonCompareMode.STRICT);
    verify(serviceRegistry).probeServices(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldAnswerAPollWithoutProbesWithAnEmptyList() {
    // given
    whenPollIsAnswered(null);

    // when / then
    activateProbes("/v2", activationBody())
        .expectStatus()
        .isOk()
        .expectBody()
        .json("{\"probes\": []}", JsonCompareMode.STRICT);
  }

  @Test
  void shouldSendThePollToTheService() {
    // given
    whenPollIsAnswered(null);
    final var captor = ArgumentCaptor.forClass(ProbeActivationRequest.class);

    // when
    activateProbes(
            "/v2",
            """
            {
              "probeIds": ["a", "b"],
              "worker": "connectors-runtime",
              "maxProbesToActivate": 10,
              "requestTimeout": 5000
            }""")
        .expectStatus()
        .isOk();

    // then
    verify(probeServices).activateProbes(captor.capture(), any(), any(), any());
    assertThat(captor.getValue())
        .isEqualTo(
            new ProbeActivationRequest(
                List.of("a", "b"),
                "connectors-runtime",
                10,
                5000L,
                List.of("<default>"),
                TenantFilter.PROVIDED));
  }

  @Test
  void shouldEndTheHeldPollOnceItIsAnswered() {
    // given - the handler yields the probes of a poll whose worker is gone (D41)
    final var cancelations = new AtomicInteger();
    doAnswer(
            invocation -> {
              final Consumer<Runnable> cancelationHandler = invocation.getArgument(2);
              cancelationHandler.accept(cancelations::incrementAndGet);
              final ResponseObserver<ProbeBatchRecord> observer = invocation.getArgument(1);
              observer.onCompleted();
              return null;
            })
        .when(probeServices)
        .activateProbes(any(), any(), any(), any());

    // when
    activateProbes("/v2", activationBody()).expectStatus().isOk();

    // then
    assertThat(cancelations).hasValue(1);
  }

  @Test
  void shouldMapTheErrorsOfAPoll() {
    // given
    doAnswer(
            invocation -> {
              final ResponseObserver<ProbeBatchRecord> observer = invocation.getArgument(1);
              observer.onError(new ServiceException("backpressure", Status.RESOURCE_EXHAUSTED));
              return null;
            })
        .when(probeServices)
        .activateProbes(any(), any(), any(), any());

    // when / then
    expectProblem(
        activateProbes("/v2", activationBody()), 503, "RESOURCE_EXHAUSTED", "backpressure");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"maxProbesToActivate\": 1}",
        "{\"probeIds\": [], \"maxProbesToActivate\": 1}",
        "{\"probeIds\": [\"a\", \"a\"], \"maxProbesToActivate\": 1}",
        "{\"probeIds\": [\"a b\"], \"maxProbesToActivate\": 1}",
        "{\"probeIds\": [\"*\"], \"maxProbesToActivate\": 1}",
        "{\"probeIds\": [\"\"], \"maxProbesToActivate\": 1}",
        "{\"probeIds\": [\"a\"]}",
        "{\"probeIds\": [\"a\"], \"maxProbesToActivate\": 0}",
        "{\"probeIds\": [\"a\"], \"maxProbesToActivate\": 1, \"tenantFilter\": \"ASSIGNED\"}"
      })
  void shouldRejectAnInvalidPollBeforeTheEngine(final String body) {
    // when / then
    activateProbes("/v2", body)
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("INVALID_ARGUMENT");
    verifyNoInteractions(probeServices);
  }

  @Test
  void shouldRejectAPollOfMoreThan100ProbeIds() {
    // given
    final String probeIds =
        IntStream.rangeClosed(1, 101)
            .mapToObj(i -> "\"id-" + i + "\"")
            .collect(Collectors.joining(", "));

    // when / then
    activateProbes("/v2", "{\"probeIds\": [%s], \"maxProbesToActivate\": 1}".formatted(probeIds))
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(probeServices);
  }

  // ---------------------------------------------------------------------------------------------
  // answerProbe
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {"/v2", PREFIXED})
  void shouldAcceptAnOkAnswer(final String prefix) {
    // given
    whenAnswerIsAccepted();
    final var captor = ArgumentCaptor.forClass(ProbeAnswerRequest.class);

    // when
    answerProbe(prefix, "{\"outcome\": \"OK\", \"data\": {\"status\": \"SUCCESS\"}}")
        .expectStatus()
        .isNoContent();

    // then
    verify(probeServices).answerProbe(captor.capture(), any());
    assertThat(captor.getValue())
        .isEqualTo(
            new ProbeAnswerRequest(PROBE_KEY, ProbeOutcome.OK, Map.of("status", "SUCCESS"), null));
    verify(serviceRegistry).probeServices(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldAcceptAnOkAnswerWithEmptyData() {
    // given - only the worker knows what its data means (D48)
    whenAnswerIsAccepted();

    // when / then
    answerProbe("/v2", "{\"outcome\": \"OK\", \"data\": {}}").expectStatus().isNoContent();
  }

  @Test
  void shouldAcceptAnUnsupportedAnswer() {
    // given
    whenAnswerIsAccepted();
    final var captor = ArgumentCaptor.forClass(ProbeAnswerRequest.class);

    // when
    answerProbe("/v2", "{\"outcome\": \"UNSUPPORTED\"}").expectStatus().isNoContent();

    // then
    verify(probeServices).answerProbe(captor.capture(), any());
    assertThat(captor.getValue())
        .isEqualTo(new ProbeAnswerRequest(PROBE_KEY, ProbeOutcome.UNSUPPORTED, null, null));
  }

  @Test
  void shouldAcceptAnErrorAnswer() {
    // given
    whenAnswerIsAccepted();
    final var captor = ArgumentCaptor.forClass(ProbeAnswerRequest.class);
    final String message = "m".repeat(500);

    // when
    answerProbe("/v2", "{\"outcome\": \"ERROR\", \"message\": \"%s\"}".formatted(message))
        .expectStatus()
        .isNoContent();

    // then
    verify(probeServices).answerProbe(captor.capture(), any());
    assertThat(captor.getValue())
        .isEqualTo(new ProbeAnswerRequest(PROBE_KEY, ProbeOutcome.ERROR, null, message));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"outcome\": \"OK\"}",
        "{\"outcome\": \"OK\", \"data\": null}",
        "{\"outcome\": \"OK\", \"data\": {}, \"message\": \"m\"}",
        "{\"outcome\": \"UNSUPPORTED\", \"data\": {}}",
        "{\"outcome\": \"UNSUPPORTED\", \"message\": \"m\"}",
        "{\"outcome\": \"ERROR\"}",
        "{\"outcome\": \"ERROR\", \"message\": \"\"}",
        "{\"outcome\": \"ERROR\", \"message\": \"m\", \"data\": {}}",
        "{\"outcome\": \"MAYBE\"}",
        "{\"data\": {}}"
      })
  void shouldRejectAnAnswerThatDoesNotFitItsOutcome(final String body) {
    // when / then - D59
    answerProbe("/v2", body).expectStatus().isBadRequest();
    verifyNoInteractions(probeServices);
  }

  @Test
  void shouldRejectAnErrorMessageOfMoreThan500Characters() {
    // when / then
    answerProbe("/v2", "{\"outcome\": \"ERROR\", \"message\": \"%s\"}".formatted("m".repeat(501)))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("INVALID_ARGUMENT");
    verifyNoInteractions(probeServices);
  }

  @Test
  void shouldMapTheServiceErrorsOfAnAnswer() {
    // given
    when(probeServices.answerProbe(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(new ServiceException("ended", Status.NOT_FOUND)),
            CompletableFuture.failedFuture(
                new ServiceException("not activated", Status.INVALID_STATE)),
            CompletableFuture.failedFuture(new ServiceException("not allowed", Status.FORBIDDEN)),
            CompletableFuture.failedFuture(
                new ServiceException("backpressure", Status.RESOURCE_EXHAUSTED)));
    final String body = "{\"outcome\": \"UNSUPPORTED\"}";

    // when / then
    expectProblem(answerProbe("/v2", body), 404, "NOT_FOUND", "ended");
    expectProblem(answerProbe("/v2", body), 409, "INVALID_STATE", "not activated");
    expectProblem(answerProbe("/v2", body), 403, "FORBIDDEN", "not allowed");
    expectProblem(answerProbe("/v2", body), 503, "RESOURCE_EXHAUSTED", "backpressure");
  }

  @Test
  void shouldAnswerPayloadTooLargeWhenTheAnswerDoesNotFitInALogBatch() {
    // given - D53
    when(probeServices.answerProbe(any(), any()))
        .thenReturn(CompletableFuture.failedFuture(tooLarge(ProbeIntent.ANSWER)));

    // when / then
    expectProblem(
        answerProbe("/v2", "{\"outcome\": \"OK\", \"data\": {\"items\": []}}"),
        413,
        "PAYLOAD_TOO_LARGE",
        "The answer is too large to be written to the log. Answer with a short ERROR instead.");
  }

  // ---------------------------------------------------------------------------------------------

  private ResponseSpec createProbe(final String prefix, final String body) {
    return post(prefix + "/probes", body);
  }

  private ResponseSpec activateProbes(final String prefix, final String body) {
    return post(prefix + "/probes/activation", body);
  }

  private ResponseSpec answerProbe(final String prefix, final String body) {
    return post(prefix + "/probes/" + PROBE_KEY + "/answer", body);
  }

  private ResponseSpec post(final String uri, final String body) {
    return webClient
        .post()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange();
  }

  private static void expectProblem(
      final ResponseSpec response, final int status, final String title, final String detail) {
    response
        .expectStatus()
        .isEqualTo(status)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo(title)
        .jsonPath("$.status")
        .isEqualTo(status)
        .jsonPath("$.detail")
        .isEqualTo(detail);
  }

  private static String createBody() {
    return "{\"probeId\": \"%s\", \"input\": \"={region: \\\"eu-west-1\\\"}\"}".formatted(PROBE_ID);
  }

  private static String activationBody() {
    return "{\"probeIds\": [\"%s\"], \"maxProbesToActivate\": 10}".formatted(PROBE_ID);
  }

  private void whenProbeEnds(final ProbeRecord probe) {
    when(probeServices.createProbe(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new ProbeResult(PROBE_KEY, probe)));
  }

  private void whenPollIsAnswered(final ProbeBatchRecord batch) {
    doAnswer(
            invocation -> {
              final ResponseObserver<ProbeBatchRecord> observer = invocation.getArgument(1);
              if (batch != null) {
                observer.onNext(batch);
              }
              observer.onCompleted();
              return null;
            })
        .when(probeServices)
        .activateProbes(any(), any(), any(), any());
  }

  private void whenAnswerIsAccepted() {
    when(probeServices.answerProbe(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new ProbeRecord()));
  }

  private static ProbeRecord answered(final ProbeOutcome outcome) {
    return new ProbeRecord()
        .setProbeId(PROBE_ID)
        .setWorker("connectors-runtime")
        .setOutcome(outcome);
  }

  private static ProbeRecord ended(final ProbeEndReason endReason, final String message) {
    return new ProbeRecord().setProbeId(PROBE_ID).setEndReason(endReason).setMessage(message);
  }

  private static ServiceException tooLarge(final ProbeIntent intent) {
    final var rejection =
        new BrokerRejection(intent, PROBE_KEY, RejectionType.EXCEEDED_BATCH_RECORD_SIZE, "");
    return new ServiceException(
        "Command '%s' rejected with code 'EXCEEDED_BATCH_RECORD_SIZE': ".formatted(intent),
        Status.INTERNAL,
        new BrokerRejectionException(rejection));
  }

  private static UnsafeBuffer buffer(final String json) {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(json));
  }
}
