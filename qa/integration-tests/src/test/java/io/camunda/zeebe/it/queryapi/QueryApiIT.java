/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.queryapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.CancelProcessInstanceResponse;
import io.camunda.zeebe.client.api.response.CompleteJobResponse;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.it.queryapi.util.TestAuthorizationClientInterceptor;
import io.camunda.zeebe.it.queryapi.util.TestAuthorizationListener;
import io.camunda.zeebe.it.queryapi.util.TestAuthorizationServerInterceptor;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.test.util.grpc.CloseAwareListener;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.test.util.testcontainers.ZeebeTestContainerDefaults;
import io.grpc.StatusRuntimeException;
import io.zeebe.containers.ZeebeContainer;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.bytebuddy.ByteBuddy;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
final class QueryApiIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(QueryApiIT.class);
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("tenantA.process")
          .startEvent()
          .serviceTask("task", b -> b.zeebeJobType("type"))
          .endEvent()
          .done();
  private static long processDefinitionKey;

  @Container
  private static final ZeebeContainer BROKER =
      new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
          .withEnv("ZEEBE_BROKER_EXPERIMENTAL_QUERYAPI_ENABLED", "true")
          .withEnv("ZEEBE_BROKER_GATEWAY_INTERCEPTORS_0_ID", "auth")
          .withEnv(
              "ZEEBE_BROKER_GATEWAY_INTERCEPTORS_0_CLASSNAME",
              TestAuthorizationServerInterceptor.class.getName())
          .withEnv("ZEEBE_BROKER_GATEWAY_INTERCEPTORS_0_JARPATH", "/tmp/interceptor.jar")
          .withCopyFileToContainer(
              MountableFile.forHostPath(createInterceptorJar().getAbsolutePath()),
              "/tmp/interceptor.jar");

  @SuppressWarnings("unused")
  @RegisterExtension
  private static final ContainerLogsDumper CONTAINER_LOGS_DUMPER =
      new ContainerLogsDumper(() -> Map.of("broker", BROKER), LOGGER);

  @BeforeAll
  static void beforeAll() {
    try (final var client = createZeebeClient("beforeAll")) {
      final var deployment =
          client.newDeployCommand().addProcessModel(PROCESS, "process.bpmn").send().join();
      processDefinitionKey = deployment.getProcesses().get(0).getProcessDefinitionKey();
    }
  }

  @Test
  void shouldAuthorizeCreateProcessInstance() {
    // given
    try (final var client = createZeebeClient("tenantA")) {
      // when
      final Future<ProcessInstanceEvent> result =
          client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send();

      // then
      assertThat(result).succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldDenyCreateProcessInstance() {
    // given
    try (final var client = createZeebeClient("tenantB")) {
      // when
      final Future<ProcessInstanceEvent> result =
          client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(StatusRuntimeException.class)
          .withMessage(
              "PERMISSION_DENIED: Failed to create process instance as you are not authorized on "
                  + "resource tenantA.process");
    }
  }

  @Test
  void shouldAuthorizeCompleteJob() {
    // given
    final long jobKey = activateJob().getKey();

    // when
    try (final var client = createZeebeClient("tenantA")) {
      final Future<CompleteJobResponse> result = client.newCompleteCommand(jobKey).send();

      // then
      assertThat(result).succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldDenyCompleteJob() {
    // given
    final long jobKey = activateJob().getKey();

    // when
    try (final var client = createZeebeClient("tenantB")) {
      final Future<CompleteJobResponse> result = client.newCompleteCommand(jobKey).send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(StatusRuntimeException.class)
          .withMessage(
              "PERMISSION_DENIED: Failed to complete job as you are not authorized on resource tenantA.process");
    }
  }

  @Test
  void shouldAuthorizeCancelProcessInstance() {
    // given
    final long processInstanceKey = activateProcess();

    // when
    try (final var client = createZeebeClient("tenantA")) {
      final Future<CancelProcessInstanceResponse> result =
          client.newCancelInstanceCommand(processInstanceKey).send();

      // then
      assertThat(result).succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldDenyCancelProcessInstance() {
    // given
    final long processInstanceKey = activateProcess();

    // when
    try (final var client = createZeebeClient("tenantB")) {
      final Future<CancelProcessInstanceResponse> result =
          client.newCancelInstanceCommand(processInstanceKey).send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(StatusRuntimeException.class)
          .withMessage(
              "PERMISSION_DENIED: Failed to cancel process instance as you are not authorized on resource tenantA.process");
    }
  }

  /**
   * Creates a process instance and activates a job. The job acts as a marker to guarantee that the
   * instance is activate. This would normally be done via an exporter, but as we still lack an IPC
   * exporter, we can use this workaround for now.
   */
  private long activateProcess() {
    return activateJob().getProcessInstanceKey();
  }

  private ActivatedJob activateJob() {
    final List<ActivatedJob> jobs = new CopyOnWriteArrayList<>();
    final JobHandler handler = (client, job) -> jobs.add(job);
    try (final var client = createZeebeClient("tenantA");
        final var worker = client.newWorker().jobType("type").handler(handler).open()) {
      client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();
      Awaitility.await("until one job is activated")
          .untilAsserted(() -> assertThat(jobs).isNotEmpty());
    }

    return jobs.get(0);
  }

  private static ZeebeClient createZeebeClient(final String tenant) {
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(BROKER.getExternalGatewayAddress())
        .withInterceptors(new TestAuthorizationClientInterceptor(tenant))
        .defaultRequestTimeout(Duration.ofMinutes(1))
        .usePlaintext()
        .build();
  }

  /**
   * Creates a JAR on the fly containing the {@link TestAuthorizationServerInterceptor}. For any
   * type which is not part of the distribution but is required by the server interceptor, you will
   * have to add it as a required type, including nested types.
   *
   * <p>NOTE: all types must be public, otherwise ByteBuddy will not be able to inject them!
   *
   * @return a JAR containing all types required by our test interceptor
   */
  private static File createInterceptorJar() {
    final var byteBuddy = new ByteBuddy();
    final File jar;

    try {
      final var baseDir = Files.createTempDirectory("jarTemp").toFile();
      jar =
          byteBuddy
              .decorate(TestAuthorizationServerInterceptor.class)
              .require(
                  byteBuddy.decorate(TestAuthorizationServerInterceptor.NoopListener.class).make())
              .require(byteBuddy.decorate(TestAuthorizationListener.class).make())
              .require(byteBuddy.decorate(CloseAwareListener.class).make())
              .require(byteBuddy.decorate(TestAuthorizationListener.Authorization.class).make())
              .make()
              .toJar(new File(baseDir, "interceptor.jar"));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return jar;
  }
}
