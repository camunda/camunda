/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.reindex.Destination;
import co.elastic.clients.elasticsearch.core.reindex.Source;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import io.camunda.client.CamundaClient;
import io.camunda.db.search.engine.config.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Currently working for Tasklist migrations, could be enhanced and improved to support other
 * migrations as well
 */
public class MigrationITInvocationProvider
    implements TestTemplateInvocationContextProvider, AfterAllCallback, BeforeAllCallback {

  private static final String API_V2 = "V2";

  private final Map<DatabaseType, Map<String, String>> zeebeEnvironmentBefore = new HashMap<>();
  private final Map<DatabaseType, Map<String, String>> zeebeEnvironmentAfter = new HashMap<>();
  private final Map<DatabaseType, Map<String, String>> tasklistEnvironmentBefore = new HashMap<>();
  private final Map<DatabaseType, Map<String, String>> tasklistEnvironmentAfter = new HashMap<>();

  private final Map<DatabaseType, TestStandaloneBroker> testBrokers = new HashMap<>();
  private BiConsumer<CamundaClient, TasklistMigrationHelper> beforeMigrationFunc;
  private BiConsumer<CamundaClient, TasklistMigrationHelper> afterMigrationFunc;
  private final Map<DatabaseType, CamundaClient> camundaClients = new HashMap<>();
  private final List<AutoCloseable> closeables = new ArrayList<>();
  private final Map<DatabaseType, TasklistMigrationHelper> tasklistContainer = new HashMap<>();
  private final Map<DatabaseType, ZeebeMigrationHelper> zeebeUtils = new HashMap<>();
  private final List<DatabaseType> databaseTypes = new ArrayList<>();
  private final Map<DatabaseType, String> databaseExternalUrls = new HashMap<>();

  @Override
  public void afterAll(final ExtensionContext extensionContext) {
    tasklistContainer.forEach((k, v) -> v.stop());
    zeebeUtils.forEach((k, v) -> v.stop());
  }

  @Override
  public void beforeAll(final ExtensionContext extensionContext) throws Exception {
    databaseTypes.forEach(
        db -> {
          zeebeEnvironmentBefore.putIfAbsent(db, new HashMap<>());
          zeebeEnvironmentAfter.putIfAbsent(db, new HashMap<>());
          tasklistEnvironmentBefore.putIfAbsent(db, new HashMap<>());
          tasklistEnvironmentAfter.putIfAbsent(db, new HashMap<>());
        });
    databaseTypes.parallelStream()
        .forEach(
            db -> {
              final Network network = Network.newNetwork();
              zeebeUtils.put(db, new ZeebeMigrationHelper(network));
              tasklistContainer.put(db, new TasklistMigrationHelper(network));

              startDatabaseContainer(db, network);

              /* Startup Sequence 8.6*/

              beforeMigrationStartup(db);

              // Stop Zeebe and Tasklist
              zeebeUtils.get(db).stop();
              tasklistContainer.get(db).stop();

              /* Startup Sequence 8.7*/

              afterMigrationStartup(db);
            });
  }

  private TestTemplateInvocationContext invocationContext(
      final TasklistMigrationHelper.UserTaskArg params, final DatabaseType databaseType) {
    return new TestTemplateInvocationContext() {

      @Override
      public String getDisplayName(final int invocationIndex) {
        return databaseType.name() + " - " + params;
      }

      @Override
      public List<Extension> getAdditionalExtensions() {
        return List.of(
            new ParameterResolver() {
              @Override
              public boolean supportsParameter(
                  final ParameterContext parameterContext, final ExtensionContext extensionContext)
                  throws ParameterResolutionException {
                return Set.of(
                        CamundaClient.class,
                        TasklistMigrationHelper.class,
                        TasklistMigrationHelper.UserTaskArg.class)
                    .contains(parameterContext.getParameter().getType());
              }

              @Override
              public Object resolveParameter(
                  final ParameterContext parameterContext, final ExtensionContext extensionContext)
                  throws ParameterResolutionException {
                if (parameterContext.getParameter().getType().equals(CamundaClient.class)) {
                  return camundaClients.get(databaseType);
                } else if (parameterContext
                    .getParameter()
                    .getType()
                    .equals(TasklistMigrationHelper.class)) {
                  return tasklistContainer.get(databaseType);
                } else if (parameterContext
                    .getParameter()
                    .getType()
                    .equals(TasklistMigrationHelper.UserTaskArg.class)) {
                  return params;
                }
                throw new ParameterResolutionException("Unsupported parameter type");
              }
            });
      }
    };
  }

  @Override
  public boolean supportsTestTemplate(final ExtensionContext extensionContext) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext extensionContext) {
    final String testCaseName = extensionContext.getTestMethod().get().getName();
    if (testCaseName.toLowerCase().contains("update")) {
      return databaseTypes.stream()
          .flatMap(db -> zeebeTasksForUpdate(db).map(p -> invocationContext(p, db)));
    } else if (testCaseName.toLowerCase().contains("jobworker")) {
      return databaseTypes.stream()
          .flatMap(db -> jobWorkers(db).map(p -> invocationContext(p, db)));
    } else if (testCaseName.toLowerCase().contains("zeebe")) {
      return databaseTypes.stream()
          .flatMap(db -> zeebeTasks(db).map(p -> invocationContext(p, db)));
    } else {
      throw new IllegalArgumentException("Unsupported test case: " + testCaseName);
    }
  }

  public MigrationITInvocationProvider withRunBefore(
      final BiConsumer<CamundaClient, TasklistMigrationHelper> runBefore) {
    beforeMigrationFunc = runBefore;
    return this;
  }

  public MigrationITInvocationProvider withRunAfter(
      final BiConsumer<CamundaClient, TasklistMigrationHelper> runAfter) {
    afterMigrationFunc = runAfter;
    return this;
  }

  public MigrationITInvocationProvider withDatabaseTypes(final DatabaseType... databaseTypes) {
    this.databaseTypes.addAll(List.of(databaseTypes));
    return this;
  }

  private Stream<TasklistMigrationHelper.UserTaskArg> zeebeTasks(final DatabaseType db) {
    return tasklistContainer
        .get(db)
        .generatedTasks
        .get(TaskImplementation.ZEEBE_USER_TASK)
        .stream();
  }

  public Stream<TasklistMigrationHelper.UserTaskArg> zeebeTasksForUpdate(final DatabaseType db) {
    return tasklistContainer.get(db).generatedTasks.get(TaskImplementation.ZEEBE_USER_TASK).stream()
        .filter(t -> t.apiVersion().equals(API_V2));
  }

  private Stream<TasklistMigrationHelper.UserTaskArg> jobWorkers(final DatabaseType db) {
    return tasklistContainer.get(db).generatedTasks.get(TaskImplementation.JOB_WORKER).stream();
  }

  private void startDatabaseContainer(final DatabaseType db, final Network network) {
    switch (db) {
      case ELASTICSEARCH:
        final ElasticsearchContainer elasticsearchContainer =
            TestSearchContainers.createDefeaultElasticsearchContainer()
                .withNetwork(network)
                .withNetworkAliases("elasticsearch");
        elasticsearchContainer.start();
        databaseExternalUrls.put(db, "http://" + elasticsearchContainer.getHttpHostAddress());
        closeables.add(elasticsearchContainer);
        break;
      case OPENSEARCH:
        final OpensearchContainer opensearchContainer =
            TestSearchContainers.createDefaultOpensearchContainer()
                .withNetwork(network)
                .withNetworkAliases("opensearch");
        opensearchContainer.start();
        databaseExternalUrls.put(db, opensearchContainer.getHttpHostAddress());
        closeables.add(opensearchContainer);
        break;
      default:
        throw new IllegalArgumentException("Unsupported database type: " + db);
    }
  }

  private Consumer<ExporterCfg> getExporterConfig(final DatabaseType databaseType) {
    final Map<String, String> connect =
        databaseType.equals(DatabaseType.ELASTICSEARCH)
            ? Map.of("url", databaseExternalUrls.get(databaseType))
            : Map.of("url", databaseExternalUrls.get(databaseType), "type", "opensearch");
    return cfg -> {
      cfg.setClassName("io.camunda.exporter.CamundaExporter");
      cfg.setArgs(
          Map.of(
              "connect",
              connect,
              "bulk",
              Map.of("size", 10, "delay", 1),
              "index",
              Map.of("shouldWaitForImporters", false)));
    };
  }

  private void beforeMigrationStartup(final DatabaseType db) {
    closeables.add(zeebeUtils.get(db).start86Broker(zeebeEnvironmentBefore.get(db), db));
    camundaClients.put(db, zeebeUtils.get(db).getCamundaClient());

    // Start Tasklist
    closeables.add(
        tasklistContainer
            .get(db)
            .createTasklist(tasklistEnvironmentBefore.get(db), false, zeebeUtils.get(db), db));

    if (beforeMigrationFunc != null) {
      beforeMigrationFunc.accept(camundaClients.get(db), tasklistContainer.get(db));
    }
  }

  private void afterMigrationStartup(final DatabaseType db) {
    zeebeEnvironmentAfter.get(db).put("camunda.database.url", databaseExternalUrls.get(db));
    final var broker =
        zeebeUtils.get(db).start87Broker(getExporterConfig(db), zeebeEnvironmentAfter.get(db), db);

    try {
      cloneProcesses(db);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    // Start Tasklist
    closeables.add(
        tasklistContainer
            .get(db)
            .createTasklist(tasklistEnvironmentAfter.get(db), true, zeebeUtils.get(db), db));

    testBrokers.put(db, broker);
    camundaClients.put(db, zeebeUtils.get(db).getCamundaClient());

    if (afterMigrationFunc != null) {
      afterMigrationFunc.accept(camundaClients.get(db), tasklistContainer.get(db));
    }

    closeables.add(broker);
  }

  private void cloneProcesses(final DatabaseType type) throws IOException {
    switch (type) {
      case ELASTICSEARCH:
        final var cfg = new ConnectConfiguration();
        cfg.setUrl(databaseExternalUrls.get(type));
        cfg.setType("elasticsearch");
        final var connector = new ElasticsearchConnector(cfg);
        final var esClient = connector.createClient();

        Awaitility.await()
            .until(
                () ->
                    esClient
                        .indices()
                        .get(GetIndexRequest.of(req -> req.index("*")))
                        .get("operate-process-8.3.0_")
                        != null);

        // Copy previous tasklist-process to operate-process, required for V2 APIs
        esClient.reindex(
            r ->
                r.source(Source.of(s -> s.index("tasklist-process-8.4.0_")))
                    .dest(Destination.of(d -> d.index("operate-process-8.3.0_")))
                    .script(
                        Script.of(
                            s ->
                                s.inline(
                                    i ->
                                        i.source(
                                                "ctx._source.isPublic = ctx._source.remove('startedByForm')")
                                            .lang("painless")))));

        break;
      case OPENSEARCH:
        final var osCfg = new ConnectConfiguration();
        osCfg.setUrl(databaseExternalUrls.get(type));
        osCfg.setType("opensearch");
        final var osConnector = new OpensearchConnector(osCfg);
        final var osClient = osConnector.createClient();

        Awaitility.await()
            .until(
                () ->
                    osClient
                        .indices()
                        .get(
                            org.opensearch.client.opensearch.indices.GetIndexRequest.of(
                                req -> req.index("*")))
                        .get("operate-process-8.3.0_")
                        != null);

        // Copy previous tasklist-process to operate-process, required for V2 APIs
        osClient.reindex(
            r ->
                r.source(
                        org.opensearch.client.opensearch.core.reindex.Source.of(
                            s -> s.index("tasklist-process-8.4.0_")))
                    .dest(
                        org.opensearch.client.opensearch.core.reindex.Destination.of(
                            d -> d.index("operate-process-8.3.0_")))
                    .script(
                        org.opensearch.client.opensearch._types.Script.of(
                            s ->
                                s.inline(
                                    i ->
                                        i.source(
                                                "ctx._source.isPublic = ctx._source.remove('startedByForm')")
                                            .lang("painless")))));

        break;
      default:
        throw new IllegalArgumentException("Unsupported database type: " + type);
    }
  }

  public enum DatabaseType {
    ELASTICSEARCH,
    OPENSEARCH
  }
}
