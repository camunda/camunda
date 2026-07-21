/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.recover;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.ProblemException;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.debug.cli.Main;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * End-to-end test for {@code cdbg recover process-definitions}: deploy process definitions, take a
 * partition-1 snapshot, simulate loss by deleting a document from secondary storage, run the
 * recovery command against the (still running) broker's snapshot, and assert the lost definition is
 * restored while the surviving one is left untouched.
 *
 * <p>Reads the snapshot in-pod while the broker is live — no outage — exercising the default
 * execution model. RDBMS is not supported by the command yet and is gated out.
 */
@MultiDbTest
@DisabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "rdbms.*$",
    disabledReason = "recover process-definitions supports Elasticsearch/OpenSearch only")
@DisabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "AWS_OS",
    disabledReason = "AWS OpenSearch requires a dedicated client not built by this test")
@DisabledIfSystemProperty(
    named = "test.integration.camunda.physical-tenant",
    matches = ".+",
    disabledReason =
        "Recover drives the debug CLI against a single secondary-storage connection and deletes"
            + " documents there directly; the tenant-scoped read path targets a different store,"
            + " so the deletion never surfaces as missing")
public class RecoverProcessDefinitionsMultiDbIT {

  private static final int TIMEOUT = 60;
  private static final Path WORKING_DIRECTORY = createWorkingDirectory();

  private static CamundaClient client;

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withWorkingDirectory(WORKING_DIRECTORY)
          .withUnauthenticatedAccess();

  @Test
  void shouldRecoverLostProcessDefinition() throws Exception {
    // given — two deployed, exported process definitions
    final long lostKey = deployProcessModel("recover-lost");
    final long survivingKey = deployProcessModel("recover-surviving");

    final ConnectConfiguration connectConfiguration =
        BROKER.bean(SearchEngineConnectProperties.class);
    final boolean isElasticsearch = connectConfiguration.getTypeEnum().isElasticSearch();
    final String processIndexName =
        new IndexDescriptors(connectConfiguration.getIndexPrefix(), isElasticsearch)
            .get(ProcessIndex.class)
            .getFullQualifiedName();

    // capture both definitions in a partition-1 snapshot (partition 1 is the deployment partition)
    final String snapshotId = takePartitionOneSnapshot();

    // simulate secondary-storage loss: drop one process-definition document
    deleteDocument(connectConfiguration, processIndexName, lostKey);
    awaitProcessDefinitionMissing(lostKey);

    // when — recover from the snapshot while the broker keeps running (in-pod, no outage)
    final ExecResult result = runRecover(connectConfiguration, snapshotId);

    // then — command succeeded and reports one recovered, one already present
    assertThat(result.exitCode).as(result.err).isZero();
    assertThat(result.out).contains("written=1").contains("present=1");

    // the lost definition is queryable again; the surviving one is still there
    awaitProcessDefinitionPresent(lostKey);
    awaitProcessDefinitionPresent(survivingKey);
  }

  private ExecResult runRecover(
      final ConnectConfiguration connectConfiguration, final String snapshotId) throws Exception {
    final Path partitionOne = partitionOneDirectory();

    final List<String> args = new ArrayList<>();
    args.add("recover");
    args.add("process-definitions");
    args.add("--root=" + partitionOne);
    args.add("--snapshot=" + snapshotId);
    args.add("--connect-type=" + connectType(connectConfiguration));
    args.add("--connect-url=" + connectConfiguration.getUrl());
    args.add("--index-prefix=" + connectConfiguration.getIndexPrefix());
    if (isNotBlank(connectConfiguration.getUsername())) {
      args.add("--connect-username=" + connectConfiguration.getUsername());
    }
    if (isNotBlank(connectConfiguration.getPassword())) {
      args.add("--connect-password=" + connectConfiguration.getPassword());
    }

    final StringWriter out = new StringWriter();
    final StringWriter err = new StringWriter();
    final int exitCode;
    try (final PrintWriter outWriter = new PrintWriter(out);
        final PrintWriter errWriter = new PrintWriter(err)) {
      exitCode =
          new picocli.CommandLine(new Main())
              .setOut(outWriter)
              .setErr(errWriter)
              .execute(args.toArray(String[]::new));
    }
    return new ExecResult(exitCode, out.toString(), err.toString());
  }

  private static String connectType(final ConnectConfiguration connectConfiguration) {
    return connectConfiguration.getTypeEnum() == DatabaseType.OPENSEARCH
        ? "opensearch"
        : "elasticsearch";
  }

  private void deleteDocument(
      final ConnectConfiguration connectConfiguration, final String indexName, final long key)
      throws Exception {
    try (final ClientAdapter adapter = ClientAdapter.of(connectConfiguration)) {
      final var batchRequest = adapter.createBatchRequest();
      batchRequest.delete(indexName, String.valueOf(key));
      batchRequest.executeWithRefresh();
    }
  }

  private String takePartitionOneSnapshot() {
    final PartitionsActuator partitions = PartitionsActuator.of(BROKER);
    partitions.takeSnapshot();
    return Awaitility.await("until a partition-1 snapshot exists")
        .atMost(Duration.ofSeconds(TIMEOUT))
        .until(
            () -> Optional.ofNullable(partitions.query().get(1).snapshotId()), Optional::isPresent)
        .orElseThrow();
  }

  private Path partitionOneDirectory() throws Exception {
    // The partition group directory is named after the physical tenant ("default"); glob for it
    // rather than hardcoding the name.
    try (final Stream<Path> groups = Files.list(WORKING_DIRECTORY.resolve("data"))) {
      return groups
          .map(group -> group.resolve("partitions").resolve("1"))
          .filter(Files::isDirectory)
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("No partition-1 directory found"));
    }
  }

  private long deployProcessModel(final String bpmnProcessId) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(bpmnProcessId).startEvent().endEvent().done();
    final long processDefinitionKey =
        client
            .newDeployResourceCommand()
            .addProcessModel(model, bpmnProcessId + ".bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    awaitProcessDefinitionPresent(processDefinitionKey);
    return processDefinitionKey;
  }

  private void awaitProcessDefinitionPresent(final long processDefinitionKey) {
    Awaitility.await("until process definition " + processDefinitionKey + " is queryable")
        .atMost(Duration.ofSeconds(2 * TIMEOUT))
        .untilAsserted(
            () -> {
              final Future<?> request =
                  client.newProcessDefinitionGetRequest(processDefinitionKey).send();
              assertThat(request).succeedsWithin(Duration.ofSeconds(TIMEOUT));
            });
  }

  private void awaitProcessDefinitionMissing(final long processDefinitionKey) {
    Awaitility.await("until process definition " + processDefinitionKey + " is gone")
        .atMost(Duration.ofSeconds(2 * TIMEOUT))
        .untilAsserted(
            () -> {
              final Future<?> request =
                  client.newProcessDefinitionGetRequest(processDefinitionKey).send();
              assertThat(request)
                  .failsWithin(Duration.ofSeconds(TIMEOUT))
                  .withThrowableOfType(ExecutionException.class)
                  .extracting(Throwable::getCause)
                  .asInstanceOf(InstanceOfAssertFactories.type(ProblemException.class))
                  .extracting(ProblemException::details)
                  .asInstanceOf(InstanceOfAssertFactories.type(ProblemDetail.class))
                  .extracting(ProblemDetail::getStatus)
                  .isEqualTo(404);
            });
  }

  private static boolean isNotBlank(final String value) {
    return value != null && !value.isBlank();
  }

  private static Path createWorkingDirectory() {
    try {
      return Files.createTempDirectory("cdbg-recover-it-");
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create working directory", e);
    }
  }

  private record ExecResult(int exitCode, String out, String err) {}
}
