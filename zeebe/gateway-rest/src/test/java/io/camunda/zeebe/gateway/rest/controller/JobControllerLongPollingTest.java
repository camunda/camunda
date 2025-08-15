/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ApiServicesExecutorProvider;
import io.camunda.service.JobServices;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.gateway.api.job.ActivateJobsStub;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResult;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.controller.util.ResettableJobActivationRequestResponseObserver;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import org.springframework.util.unit.DataSize;

@WebMvcTest(JobController.class)
public class JobControllerLongPollingTest extends RestControllerTest {

  static final String JOBS_BASE_URL = "/v2/jobs";

  @Autowired ActivateJobsHandler<JobActivationResult> activateJobsHandler;
  @Autowired StubbedBrokerClient stubbedBrokerClient;
  @SpyBean ResettableJobActivationRequestResponseObserver responseObserver;
  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setup() {
    responseObserver.reset();
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldActivateJobsImmediatelyIfAvailable() {
    // given
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.addAvailableJobs("TEST-immediate", 2);
    stub.registerWith(stubbedBrokerClient);

    final var request =
        """
        {
          "type": "TEST-immediate",
          "maxJobsToActivate": 2,
          "requestTimeout": 100,
          "timeout": 100,
          "fetchVariable": [],
          "tenantIds": [],
          "worker": "bar"
        }""";

    // when / then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/activation")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.jobs[0].type")
        .isEqualTo("TEST-immediate")
        .jsonPath("$.jobs[1].type")
        .isEqualTo("TEST-immediate");

    Mockito.verify(responseObserver, Mockito.times(1)).onNext(any());
    Mockito.verify(responseObserver).onCompleted();
  }

  @Test
  void shouldReturnNoJobsImmediatelyIfNoneAvailable() {
    // given
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.registerWith(stubbedBrokerClient);

    final var request =
        """
        {
          "type": "TEST-no-jobs",
          "maxJobsToActivate": 10,
          "requestTimeout": 100,
          "timeout": 100,
          "fetchVariable": ["foo"],
          "tenantIds": [],
          "worker": "bar"
        }""";
    final var expectedBody =
        """
        {
          "jobs": []
        }""";
    // when / then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/activation")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verify(responseObserver, Mockito.never()).onNext(any());
    Mockito.verify(responseObserver).onCompleted();
  }

  @Test
  void shouldActivateJobsRoundRobin() {
    // given
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.registerWith(stubbedBrokerClient);

    final var request =
        """
        {
          "type": "TEST-round-robin",
          "maxJobsToActivate": 2,
          "requestTimeout": 100,
          "timeout": 100,
          "tenantIds": [],
          "worker": "bar"
        }""";

    /*
     * Get the baseline partition since the current one could be any partition.
     * The job activation handler is created once for all tests, so previous tests can have moved
     * the round-robin index by any number already.
     */
    stub.addAvailableJobs("TEST-round-robin", 1);
    final String result =
        webClient
            .post()
            .uri(JOBS_BASE_URL + "/activation")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    final int basePartition =
        Protocol.decodePartitionId(Long.parseLong(JsonPath.read(result, "$.jobs[0].jobKey")));
    final int partitionsCount =
        stubbedBrokerClient.getTopologyManager().getTopology().getPartitionsCount();

    // try activating jobs on each partition round-robin
    for (int partitionOffset = 1; partitionOffset <= partitionsCount; partitionOffset++) {
      // calculate the expected partition ID to build the assertion key for
      final int expectedPartitionId = (basePartition + partitionOffset - 1) % partitionsCount + 1;
      // reset the results and add new jobs that can be fetched
      responseObserver.reset();
      stub.addAvailableJobs("TEST-round-robin", 2);
      // when/then
      webClient
          .post()
          .uri(JOBS_BASE_URL + "/activation")
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .expectBody()
          .jsonPath("$.jobs[0].jobKey")
          .isEqualTo(Protocol.encodePartitionId(expectedPartitionId, 0))
          .jsonPath("$.jobs[1].jobKey")
          .isEqualTo(Protocol.encodePartitionId(expectedPartitionId, 1));
    }
  }

  @Test
  void shouldSendRejectionWithoutRetrying() {
    // given
    final AtomicInteger callCounter = new AtomicInteger();
    stubbedBrokerClient.registerHandler(
        BrokerActivateJobsRequest.class,
        request -> {
          callCounter.incrementAndGet();
          return new BrokerRejectionResponse<>(
              new BrokerRejection(Intent.UNKNOWN, 1, RejectionType.INVALID_ARGUMENT, "expected"));
        });

    final var request =
        """
        {
          "type": "TEST-rejection",
          "maxJobsToActivate": 10,
          "requestTimeout": 100,
          "timeout": 100,
          "fetchVariable": ["foo"],
          "tenantIds": [],
          "worker": "bar"
        }""";
    final var expectedBody =
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "INVALID_ARGUMENT",
          "detail": "Command 'UNKNOWN' rejected with code 'INVALID_ARGUMENT': expected",
          "instance": "%s"
        }"""
            .formatted(JOBS_BASE_URL + "/activation");

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/activation")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    assertThat(callCounter).hasValue(1);
  }

  @Test
  void shouldAllowActivateJobWithAuthorizedTenantWhenMultiTenancyEnabled() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.registerWith(stubbedBrokerClient);

    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);

    final var request =
        """
        {
          "type": "TEST-authorized-tenant",
          "maxJobsToActivate": 2,
          "requestTimeout": 100,
          "timeout": 100,
          "fetchVariable": [],
          "tenantIds": ["tenantId"],
          "worker": "bar"
        }""";

    final var expectedBody =
        """
        {
          "jobs": []
        }""";

    // when then
    final ResponseSpec response =
        webClient
            .post()
            .uri(JOBS_BASE_URL + "/activation")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    response
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldAllowActivateJobWithDefaultTenantWhenMultiTenancyDisabled() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.registerWith(stubbedBrokerClient);

    final var request =
        """
        {
          "type": "TEST-default-tenant",
          "maxJobsToActivate": 2,
          "requestTimeout": 100,
          "timeout": 100,
          "fetchVariable": [],
          "tenantIds": ["<default>"],
          "worker": "bar"
        }""";

    final var expectedBody =
        """
        {
          "jobs": []
        }""";

    // when then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/activation")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @TestConfiguration
  static class TestJobApplication {

    @Bean
    public ActorClock actorClock() {
      return new ControlledActorClock();
    }

    @Bean(destroyMethod = "close")
    public ActorScheduler actorScheduler(final ActorClock clock) {
      final ActorScheduler scheduler =
          ActorScheduler.newActorScheduler()
              .setCpuBoundActorThreadCount(
                  Math.max(1, Runtime.getRuntime().availableProcessors() - 2))
              .setIoBoundActorThreadCount(2)
              .setActorClock(clock)
              .build();
      scheduler.start();
      return scheduler;
    }

    @Bean
    public StubbedBrokerClient brokerClient() {
      return new StubbedBrokerClient();
    }

    @Bean
    public ResettableJobActivationRequestResponseObserver responseObserver() {
      return new ResettableJobActivationRequestResponseObserver(new CompletableFuture<>());
    }

    @Bean
    public ResponseObserverProvider responseObserverProvider(
        final ResettableJobActivationRequestResponseObserver responseObserver) {
      return responseObserver::setResult;
    }

    @Bean
    public ActivateJobsHandler<JobActivationResult> activateJobsHandler(
        final BrokerClient brokerClient, final ActorScheduler actorScheduler) {
      final var handler =
          LongPollingActivateJobsHandler.<JobActivationResult>newBuilder()
              .setBrokerClient(brokerClient)
              .setMaxMessageSize(DataSize.ofMegabytes(4L).toBytes())
              .setActivationResultMapper(ResponseMapper::toActivateJobsResponse)
              .setNoJobsReceivedExceptionProvider(RuntimeException::new)
              .setRequestCanceledExceptionProvider(reason -> new RuntimeException(reason))
              .setMetrics(LongPollingMetrics.noop())
              .build();
      final var future = new CompletableFuture<>();
      final var actor =
          Actor.newActor()
              .name("JobActivationHandler-JobControllerLongPollingTest")
              .actorStartedHandler(handler.andThen(future::complete))
              .build();
      actorScheduler.submitActor(actor);
      return handler;
    }

    @Bean
    public JobServices<JobActivationResult> jobServices(
        final BrokerClient brokerClient,
        final ActivateJobsHandler<JobActivationResult> activateJobsHandler) {
      return new JobServices<>(
          brokerClient,
          new SecurityContextProvider(),
          activateJobsHandler,
          null,
          null,
          new ApiServicesExecutorProvider(1, 1, 1));
    }
  }
}
