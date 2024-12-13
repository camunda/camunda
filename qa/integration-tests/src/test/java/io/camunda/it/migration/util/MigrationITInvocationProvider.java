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
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
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
  private BiConsumer<ZeebeClient, TasklistUtil> beforeMigrationFunc;
  private BiConsumer<ZeebeClient, TasklistUtil> afterMigrationFunc;
  private final Map<DatabaseType, ZeebeClient> zeebeClients = new HashMap<>();
  private final List<AutoCloseable> closeables = new ArrayList<>();
  private final Map<DatabaseType, TasklistUtil> tasklistUtils = new HashMap<>();
  private final Map<DatabaseType, ZeebeUtil> zeebeUtils = new HashMap<>();
  private final List<DatabaseType> databaseTypes = new ArrayList<>();
  private String elasticsearchExternalUrl;
  private String opensearchExternalUrl;

  @Override
  public void afterAll(final ExtensionContext extensionContext) {
    tasklistUtils.forEach((k, v) -> v.stop());
    zeebeUtils.forEach((k, v) -> v.stop());
  }

  @Override
  public void beforeAll(final ExtensionContext extensionContext) throws Exception {
    databaseTypes.stream()
        .parallel()
        .forEach(
            db -> {
              switch (db) {
                case ELASTICSEARCH:
                  final Network network = Network.newNetwork();
                  zeebeUtils.put(db, new ZeebeUtil(network));
                  tasklistUtils.put(db, new TasklistUtil(network));

                  final ElasticsearchContainer elasticsearchContainer =
                      TestSearchContainers.createDefeaultElasticsearchContainer()
                          .withNetwork(network)
                          .withNetworkAliases("elasticsearch");
                  elasticsearchContainer.start();
                  elasticsearchExternalUrl =
                      "http://" + elasticsearchContainer.getHttpHostAddress();
                  closeables.add(elasticsearchContainer);

                  /* Startup Sequence 8.6*/

                  updateEnvMap(db, zeebeEnvironmentBefore, zeebe86ElasticsearchDefaultConfig());

                  closeables.add(zeebeUtils.get(db).start86Broker(zeebeEnvironmentBefore.get(db)));
                  zeebeClients.put(db, zeebeUtils.get(db).getZeebeClient());

                  updateEnvMap(
                      db,
                      tasklistEnvironmentBefore,
                      tasklistElasticsearchDefaultConfig(zeebeUtils.get(db)));
                  var tasklistEs =
                      tasklistUtils
                          .get(db)
                          .createTasklist(tasklistEnvironmentBefore.get(db), false);
                  closeables.add(tasklistEs);

                  if (beforeMigrationFunc != null) {
                    beforeMigrationFunc.accept(zeebeClients.get(db), tasklistUtils.get(db));
                  }

                  /* Startup Sequence 8.7*/

                  zeebeUtils.get(db).stop();
                  tasklistUtils.get(db).stop();

                  updateEnvMap(db, zeebeEnvironmentAfter, zeebe87ElasticsearchDefaultConfig());
                  final var broker =
                      zeebeUtils
                          .get(db)
                          .start87Broker(
                              cfg -> {
                                cfg.setClassName("io.camunda.exporter.CamundaExporter");
                                cfg.setArgs(
                                    Map.of(
                                        "connect",
                                        Map.of(
                                            "url",
                                            "http://"
                                                + elasticsearchContainer.getHttpHostAddress()),
                                        "bulk",
                                        Map.of("size", 10, "delay", 1),
                                        "index",
                                        Map.of("shouldWaitForImporters", false)));
                              },
                              zeebeEnvironmentAfter.get(db));

                  try {
                    cloneProcesses(db);
                  } catch (final IOException e) {
                    throw new RuntimeException(e);
                  }

                  updateEnvMap(
                      db,
                      tasklistEnvironmentAfter,
                      tasklistElasticsearchDefaultConfig(zeebeUtils.get(db)));
                  tasklistEs =
                      tasklistUtils.get(db).createTasklist(tasklistEnvironmentAfter.get(db), true);
                  closeables.add(tasklistEs);

                  testBrokers.put(db, broker);
                  zeebeClients.put(db, zeebeUtils.get(db).getZeebeClient());

                  if (afterMigrationFunc != null) {
                    afterMigrationFunc.accept(zeebeClients.get(db), tasklistUtils.get(db));
                  }

                  closeables.add(broker);
                  break;

                case OPENSEARCH:
                  final Network networkOs = Network.newNetwork();
                  zeebeUtils.put(db, new ZeebeUtil(networkOs));
                  tasklistUtils.put(db, new TasklistUtil(networkOs));

                  final OpensearchContainer opensearchContainer =
                      TestSearchContainers.createDefaultOpensearchContainer()
                          .withNetwork(networkOs)
                          .withNetworkAliases("opensearch");
                  opensearchContainer.start();
                  opensearchExternalUrl = opensearchContainer.getHttpHostAddress();
                  closeables.add(opensearchContainer);

                  // Startup Sequence 8.6

                  updateEnvMap(db, zeebeEnvironmentBefore, zeebe86OpensearchDefaultConfig());

                  closeables.add(zeebeUtils.get(db).start86Broker(zeebeEnvironmentBefore.get(db)));
                  zeebeClients.put(db, zeebeUtils.get(db).getZeebeClient());

                  updateEnvMap(
                      db,
                      tasklistEnvironmentBefore,
                      tasklistOpensearchDefaultConfig(zeebeUtils.get(db)));
                  var tasklistOs =
                      tasklistUtils
                          .get(db)
                          .createTasklist(tasklistEnvironmentBefore.get(db), false);
                  closeables.add(tasklistOs);

                  if (beforeMigrationFunc != null) {
                    beforeMigrationFunc.accept(zeebeClients.get(db), tasklistUtils.get(db));
                  }

                  // Startup Sequence 8.7

                  zeebeUtils.get(db).stop();
                  tasklistUtils.get(db).stop();

                  updateEnvMap(db, zeebeEnvironmentAfter, zeebe87OpensearchDefaultConfig());
                  final var brokerOs =
                      zeebeUtils
                          .get(db)
                          .start87Broker(
                              cfg -> {
                                cfg.setClassName("io.camunda.exporter.CamundaExporter");
                                cfg.setArgs(
                                    Map.of(
                                        "connect",
                                        Map.of("url", opensearchExternalUrl, "type", "opensearch"),
                                        "bulk",
                                        Map.of("size", 10, "delay", 1),
                                        "index",
                                        Map.of("shouldWaitForImporters", false)));
                              },
                              zeebeEnvironmentAfter.get(db));

                  try {
                    cloneProcesses(db);
                  } catch (final IOException e) {
                    throw new RuntimeException(e);
                  }

                  updateEnvMap(
                      db,
                      tasklistEnvironmentAfter,
                      tasklistOpensearchDefaultConfig(zeebeUtils.get(db)));
                  tasklistOs =
                      tasklistUtils.get(db).createTasklist(tasklistEnvironmentAfter.get(db), true);
                  closeables.add(tasklistOs);

                  testBrokers.put(db, brokerOs);
                  zeebeClients.put(db, zeebeUtils.get(db).getZeebeClient());

                  if (afterMigrationFunc != null) {
                    afterMigrationFunc.accept(zeebeClients.get(db), tasklistUtils.get(db));
                  }

                  closeables.add(brokerOs);
                  break;
                default:
                  throw new IllegalArgumentException("Unsupported database type: " + db);
              }
            });
  }

  private TestTemplateInvocationContext invocationContext(
      final TasklistUtil.UserTaskArg params, final DatabaseType databaseType) {
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
                return Set.of(ZeebeClient.class, TasklistUtil.class, TasklistUtil.UserTaskArg.class)
                    .contains(parameterContext.getParameter().getType());
              }

              @Override
              public Object resolveParameter(
                  final ParameterContext parameterContext, final ExtensionContext extensionContext)
                  throws ParameterResolutionException {
                if (parameterContext.getParameter().getType().equals(ZeebeClient.class)) {
                  return zeebeClients.get(databaseType);
                } else if (parameterContext.getParameter().getType().equals(TasklistUtil.class)) {
                  return tasklistUtils.get(databaseType);
                } else if (parameterContext
                    .getParameter()
                    .getType()
                    .equals(TasklistUtil.UserTaskArg.class)) {
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
      final BiConsumer<ZeebeClient, TasklistUtil> runBefore) {
    beforeMigrationFunc = runBefore;
    return this;
  }

  public MigrationITInvocationProvider withRunAfter(
      final BiConsumer<ZeebeClient, TasklistUtil> runAfter) {
    afterMigrationFunc = runAfter;
    return this;
  }

  public MigrationITInvocationProvider withDatabaseTypes(final DatabaseType... databaseTypes) {
    this.databaseTypes.addAll(List.of(databaseTypes));
    return this;
  }

  private Map<String, String> zeebe86ElasticsearchDefaultConfig() {
    return new HashMap<>() {
      {
        put(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.ElasticsearchExporter");
        put("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", "http://elasticsearch:9200");
        put("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1");
        put("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
        put("CAMUNDA_DATABASE_URL", "http://elasticsearch:9200");
        put("CAMUNDA_REST_QUERY_ENABLED", "true");
      }
    };
  }

  private Map<String, String> zeebe87ElasticsearchDefaultConfig() {
    return new HashMap<>() {
      {
        put("camunda.rest.query.enabled", "true");
        put("camunda.database.url", elasticsearchExternalUrl);
      }
    };
  }

  private Map<String, String> zeebe86OpensearchDefaultConfig() {
    return new HashMap<>() {
      {
        put(
            "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.opensearch.OpensearchExporter");
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_URL", "http://opensearch:9200");
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_BULK_SIZE", "1");
        put("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
        put("CAMUNDA_DATABASE_URL", "http://opensearch:9200");
        put("CAMUNDA_REST_QUERY_ENABLED", "true");
      }
    };
  }

  private Map<String, String> zeebe87OpensearchDefaultConfig() {
    return new HashMap<>() {
      {
        put("camunda.rest.query.enabled", "true");
        put("camunda.database.url", opensearchExternalUrl);
        put("camunda.database.type", "opensearch");
      }
    };
  }

  private Map<String, String> tasklistElasticsearchDefaultConfig(final ZeebeUtil zeebe) {
    return new HashMap<>() {
      {
        put("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", "http://elasticsearch:9200");
        put("CAMUNDA_TASKLIST_ELASTICSEARCH_HOST", "elasticsearch");
        put("CAMUNDA_TASKLIST_ELASTICSEARCH_PORT", "9200");
        put("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", "http://elasticsearch:9200");
        put("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", zeebe.getZeebeGatewayAddress());
        put("CAMUNDA_TASKLIST_ZEEBE_REST_ADDRESS", zeebe.getZeebeRestAddress());
      }
    };
  }

  private Map<String, String> tasklistOpensearchDefaultConfig(final ZeebeUtil zeebe) {
    return new HashMap<>() {
      {
        put("CAMUNDA_TASKLIST_DATABASE", "opensearch");
        put("CAMUNDA_TASKLIST_OPENSEARCH_URL", "http://opensearch:9200");
        put("CAMUNDA_TASKLIST_OPENSEARCH_HOST", "opensearch");
        put("CAMUNDA_TASKLIST_OPENSEARCH_PORT", "9200");
        put("CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_URL", "http://opensearch:9200");
        put("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", zeebe.getZeebeGatewayAddress());
        put("CAMUNDA_TASKLIST_ZEEBE_REST_ADDRESS", zeebe.getZeebeRestAddress());
      }
    };
  }

  private Stream<TasklistUtil.UserTaskArg> zeebeTasks(final DatabaseType db) {
    return tasklistUtils.get(db).generatedTasks.get(TaskImplementation.ZEEBE_USER_TASK).stream();
  }

  public Stream<TasklistUtil.UserTaskArg> zeebeTasksForUpdate(final DatabaseType db) {
    return tasklistUtils.get(db).generatedTasks.get(TaskImplementation.ZEEBE_USER_TASK).stream()
        .filter(t -> t.apiVersion().equals(API_V2));
  }

  private Stream<TasklistUtil.UserTaskArg> jobWorkers(final DatabaseType db) {
    return tasklistUtils.get(db).generatedTasks.get(TaskImplementation.JOB_WORKER).stream();
  }

  private void updateEnvMap(
      final DatabaseType type,
      final Map<DatabaseType, Map<String, String>> origin,
      final Map<String, String> env) {
    final var map = origin.getOrDefault(type, new HashMap<>());
    map.putAll(env);
    origin.put(type, map);
  }

  private void cloneProcesses(final DatabaseType type) throws IOException {
    switch (type) {
      case ELASTICSEARCH:
        final var cfg = new ConnectConfiguration();
        cfg.setUrl(elasticsearchExternalUrl);
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
        osCfg.setUrl(opensearchExternalUrl);
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
