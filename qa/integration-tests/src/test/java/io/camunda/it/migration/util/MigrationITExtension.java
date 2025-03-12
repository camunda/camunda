/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import io.camunda.client.CamundaClient;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.ModifierSupport;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class MigrationITExtension
    implements AfterAllCallback,
        BeforeAllCallback,
        AfterEachCallback,
        TestTemplateInvocationContextProvider {

  private static final String TASKLIST = "tasklist";
  private static final String OPERATE = "operate";
  private static final String STORE_NAMESPACE = "MigrationITExtension";
  private static final String INDEX_PREFIX = "INDEX_PREFIX";
  private static final String DATABASE_TYPE = "DATABASE_TYPE";
  private static final String HAS_87_DATA = "HAS_87_DATA";
  private static final String MIGRATOR = "MIGRATOR";
  private static final String DATABASE_TESTER = "DATABASE_CHECKS";

  private final List<AutoCloseable> dbClosables = new ArrayList<>();
  private final Map<DatabaseType, Network> networks = new HashMap<>();
  private final Set<DatabaseType> databaseTypes =
      Set.of(DatabaseType.ELASTICSEARCH, DatabaseType.OPENSEARCH);
  private final Map<DatabaseType, String> databaseExternalUrls = new HashMap<>();
  private Map<String, String> initialEnvOverrides = new HashMap<>();

  @Override
  public void afterAll(final ExtensionContext context) {
    final var clazz = context.getTestClass().get();
    if (!isNestedClass(clazz)) {
      dbClosables.parallelStream()
          .forEach(
              c -> {
                try {
                  c.close();
                } catch (final Exception e) {
                  throw new RuntimeException(e);
                }
              });
    }
  }

  @Override
  public void afterEach(final ExtensionContext context) {
    final String indexPrefix = getStore(context).get(INDEX_PREFIX, String.class);
    final DatabaseType db = getStore(context).get(DATABASE_TYPE, DatabaseType.class);
    try {
      final CamundaMigrator migrator = getStore(context).get(MIGRATOR, CamundaMigrator.class);
      if (migrator != null) {
        migrator.close();
        migrator.cleanup();
      }
      final MigrationDatabaseChecks checks =
          getStore(context).get(DATABASE_TESTER, MigrationDatabaseChecks.class);
      if (checks != null) {
        checks.cleanup(indexPrefix);
        checks.close();
      }
    } catch (final Exception e) {
      throw new RuntimeException("Error during cleanup for database: " + db, e);
    }
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    final var clazz = context.getTestClass().get();
    if (!isNestedClass(clazz)) {
      databaseTypes.parallelStream()
          .forEach(
              db -> {
                final Network network = Network.newNetwork();
                startDatabaseContainer(db, network);
                networks.put(db, network);
              });
    }
  }

  @Override
  public boolean supportsTestTemplate(final ExtensionContext context) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext context) {
    return databaseTypes.stream()
        .flatMap(
            db -> {
              final String indexPrefix = context.getTestMethod().get().getName().toLowerCase();
              getStore(context).put(INDEX_PREFIX, indexPrefix);
              getStore(context).put(DATABASE_TYPE, db);
              final CamundaMigrator migrator = new CamundaMigrator(networks.get(db), indexPrefix);
              migrator.initialize(db, databaseExternalUrls.get(db), initialEnvOverrides);
              final var expectedDescriptors =
                  new IndexDescriptors(indexPrefix, db.equals(DatabaseType.ELASTICSEARCH)).all();
              final MigrationDatabaseChecks checks =
                  new MigrationDatabaseChecks(databaseExternalUrls.get(db), expectedDescriptors);
              getStore(context).put(MIGRATOR, migrator);
              getStore(context).put(DATABASE_TESTER, checks);
              return Stream.of(invocationContext(db));
            });
  }

  public MigrationITExtension withInitialEnvOverrides(final Map<String, String> envOverrides) {
    initialEnvOverrides = envOverrides;
    return this;
  }

  public void upgrade(final ExtensionContext context, final Map<String, String> envOverrides) {
    final DatabaseType databaseType = getStore(context).get(DATABASE_TYPE, DatabaseType.class);
    final String indexPrefix = getStore(context).get(INDEX_PREFIX, String.class);
    final MigrationDatabaseChecks tester =
        getStore(context).get(DATABASE_TESTER, MigrationDatabaseChecks.class);

    if (getStore(context).getOrDefault(HAS_87_DATA, Boolean.class, false)) {
      awaitImportersFlushed(tester, indexPrefix);
    }

    final CamundaMigrator migrator = getStore(context).get(MIGRATOR, CamundaMigrator.class);
    migrator.update(databaseType, envOverrides);

    awaitExporterReadiness(tester, indexPrefix);

    /* Ingest an 8.8 Record in order to trigger importers empty batch counting */
    ingestRecordToTriggerImporters(migrator.getCamundaClient());

    awaitImportersFinished(tester, indexPrefix);
  }

  private boolean isNestedClass(final Class<?> currentClass) {
    return !ModifierSupport.isStatic(currentClass) && currentClass.isMemberClass();
  }

  public CamundaClient getCamundaClient(final ExtensionContext context) {
    return getStore(context).get(MIGRATOR, CamundaMigrator.class).getCamundaClient();
  }

  /**
   * If the test case creates data on 8.7 version, it should be marked as such in order to wait for
   * the Tasklist & Operate import-position indices to be flushed
   */
  public void has87Data(final ExtensionContext context) {
    getStore(context).put(HAS_87_DATA, true);
  }

  public CamundaMigrator getMigrator(final ExtensionContext context) {
    return getStore(context).get(MIGRATOR, CamundaMigrator.class);
  }

  private TestTemplateInvocationContext invocationContext(final DatabaseType databaseType) {
    return new TestTemplateInvocationContext() {

      private Object resolveParam(final Class<?> param, final ExtensionContext context) {
        if (param.equals(DatabaseType.class)) {
          return getStore(context).get(DATABASE_TYPE, DatabaseType.class);
        } else if (param.equals(CamundaMigrator.class)) {
          return getStore(context).get(MIGRATOR, CamundaMigrator.class);
        } else if (param.equals(CamundaClient.class)) {
          return getStore(context).get(MIGRATOR, CamundaMigrator.class).getCamundaClient();
        } else if (param.equals(DocumentBasedSearchClient.class)) {
          return getStore(context).get(MIGRATOR, CamundaMigrator.class).getSearchClient();
        } else if (param.equals(ExtensionContext.class)) {
          return context;
        }
        return null;
      }

      @Override
      public String getDisplayName(final int invocationIndex) {
        return databaseType.name();
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
                        DatabaseType.class,
                        CamundaClient.class,
                        CamundaMigrator.class,
                        DocumentBasedSearchClient.class,
                        ExtensionContext.class)
                    .contains(parameterContext.getParameter().getType());
              }

              @Override
              public Object resolveParameter(
                  final ParameterContext parameterContext, final ExtensionContext extensionContext)
                  throws ParameterResolutionException {
                final Class<?> requestedClass = parameterContext.getParameter().getType();
                final var paramSupplier = resolveParam(requestedClass, extensionContext);
                if (paramSupplier != null) {
                  return paramSupplier;
                }
                throw new ParameterResolutionException("Unsupported parameter type");
              }
            });
      }
    };
  }

  private void startDatabaseContainer(final DatabaseType db, final Network network) {
    switch (db) {
      case ELASTICSEARCH:
        final ElasticsearchContainer elasticsearchContainer =
            TestSearchContainers.createDefeaultElasticsearchContainer()
                .withNetwork(network)
                .withNetworkAliases("elasticsearch");
        elasticsearchContainer.start();
        dbClosables.add(elasticsearchContainer);
        databaseExternalUrls.put(db, "http://" + elasticsearchContainer.getHttpHostAddress());
        break;
      case OPENSEARCH:
        final OpensearchContainer opensearchContainer =
            TestSearchContainers.createDefaultOpensearchContainer()
                .withNetwork(network)
                .withNetworkAliases("opensearch");
        opensearchContainer.start();
        databaseExternalUrls.put(db, opensearchContainer.getHttpHostAddress());
        dbClosables.add(opensearchContainer);
        break;
      default:
        throw new IllegalArgumentException("Unsupported database type: " + db);
    }
  }

  private void awaitExporterReadiness(
      final MigrationDatabaseChecks tester, final String indexPrefix) {
    Awaitility.await("Await database and exporter readiness")
        .timeout(Duration.of(1, ChronoUnit.MINUTES))
        .pollInterval(Duration.of(1, ChronoUnit.MILLIS))
        .untilAsserted(() -> tester.validateSchemaCreation(indexPrefix));
  }

  private void awaitImportersFinished(
      final MigrationDatabaseChecks tester, final String indexPrefix) {
    Awaitility.await("Await Importers finished")
        .timeout(Duration.of(1, ChronoUnit.MINUTES))
        .pollInterval(Duration.of(1, ChronoUnit.SECONDS))
        .until(
            () ->
                tester.checkImportersFinished(indexPrefix, OPERATE)
                    && tester.checkImportersFinished(indexPrefix, TASKLIST));
  }

  private void awaitImportersFlushed(
      final MigrationDatabaseChecks tester, final String indexPrefix) {
    Awaitility.await("Await Import Positions have been flushed")
        .timeout(Duration.of(1, ChronoUnit.MINUTES))
        .pollInterval(Duration.of(500, ChronoUnit.MILLIS))
        .until(
            () ->
                tester.checkImportPositionsFlushed(indexPrefix, OPERATE)
                    && tester.checkImportPositionsFlushed(indexPrefix, TASKLIST));
  }

  private void ingestRecordToTriggerImporters(final CamundaClient client) {
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/error-end-event.bpmn")
        .send()
        .join();
  }

  private static ExtensionContext.Store getStore(final ExtensionContext context) {
    return context.getStore(ExtensionContext.Namespace.create(STORE_NAMESPACE));
  }
}
