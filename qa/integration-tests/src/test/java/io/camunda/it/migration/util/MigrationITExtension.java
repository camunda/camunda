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
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
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
import org.junit.platform.commons.support.ModifierSupport;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class MigrationITExtension
    implements AfterAllCallback, BeforeAllCallback, TestTemplateInvocationContextProvider {

  private static final String TASKLIST = "tasklist";
  private static final String OPERATE = "operate";
  private static final String STORE_NAMESPACE = "MigrationITExtension";
  private static final String STORE_NAMESPACE_OS = "MigrationITExtension-OS";
  private static final String INDEX_PREFIX = "INDEX_PREFIX";
  private static final String DATABASE_TYPE = "DATABASE_TYPE";
  private static final String MIGRATOR = "MIGRATOR";
  private static final String DATABASE_TESTER = "DATABASE_CHECKS";

  private final List<AutoCloseable> closables = new ArrayList<>();
  private final Map<DatabaseType, Network> networks = new HashMap<>();
  private final Set<DatabaseType> databaseTypes =
      Set.of(DatabaseType.ELASTICSEARCH, DatabaseType.OPENSEARCH);
  private final Map<DatabaseType, String> databaseExternalUrls = new HashMap<>();

  private Map<DatabaseType, Map<String, String>> initialEnvOverrides =
      new HashMap<>() {
        {
          put(DatabaseType.ELASTICSEARCH, new HashMap<>());
          put(DatabaseType.OPENSEARCH, new HashMap<>());
        }
      };
  private Map<DatabaseType, Map<String, String>> upgradeEnvOverrides =
      new HashMap<>() {
        {
          put(DatabaseType.ELASTICSEARCH, new HashMap<>());
          put(DatabaseType.OPENSEARCH, new HashMap<>());
        }
      };
  private Path tempDir;
  private BiConsumer<DatabaseType, CamundaMigrator> beforeUpgradeConsumer = (db, migrator) -> {};

  @Override
  public void afterAll(final ExtensionContext context) throws IOException {
    final var clazz = context.getTestClass().get();
    if (!isNestedClass(clazz)) {
      CloseHelper.quietCloseAll(closables);
      FileUtil.deleteFolderIfExists(tempDir);
    }
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    final var clazz = context.getTestClass().get();
    if (!isNestedClass(clazz)) {
      tempDir = Files.createTempDirectory("migration-it");
      databaseTypes.parallelStream()
          .forEach(
              db -> {
                final Network network = Network.newNetwork();
                startDatabaseContainer(db, network);
                networks.put(db, network);
                startUpMigrator(db, context);
                beforeUpgradeConsumer.accept(
                    db, getStore(db, context).get(MIGRATOR, CamundaMigrator.class));
                upgrade(db, context, upgradeEnvOverrides.get(db));
              });
    }
  }

  /**
   * Allows to provide a consumer that will be called before the upgrade process starts. This can be
   * used to setup the database with data that is required for the upgrade process.
   *
   * @param upgradeConsumer
   * @return self for chaining
   */
  public MigrationITExtension withBeforeUpgradeConsumer(
      final BiConsumer<DatabaseType, CamundaMigrator> upgradeConsumer) {
    beforeUpgradeConsumer = upgradeConsumer;
    return this;
  }

  @Override
  public boolean supportsTestTemplate(final ExtensionContext context) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext context) {
    return databaseTypes.stream().flatMap(db -> Stream.of(invocationContext(db)));
  }

  public MigrationITExtension withInitialEnvOverrides(
      final Map<DatabaseType, Map<String, String>> envOverrides) {
    initialEnvOverrides = envOverrides;
    return this;
  }

  public MigrationITExtension withUpgradeEnvOverrides(
      final Map<DatabaseType, Map<String, String>> envOverrides) {
    upgradeEnvOverrides = envOverrides;
    return this;
  }

  private void startUpMigrator(final DatabaseType db, final ExtensionContext context) {
    final String indexPrefix = context.getTestClass().get().getSimpleName().toLowerCase();
    getStore(db, context).put(INDEX_PREFIX, indexPrefix);
    getStore(db, context).put(DATABASE_TYPE, db);
    final CamundaMigrator migrator =
        new CamundaMigrator(networks.get(db), indexPrefix, tempDir.resolve(db.name()));
    migrator.initialize(db, databaseExternalUrls.get(db), initialEnvOverrides.get(db));
    final var expectedDescriptors =
        new IndexDescriptors(indexPrefix, db.equals(DatabaseType.ELASTICSEARCH)).all();
    final MigrationDatabaseChecks checks =
        new MigrationDatabaseChecks(databaseExternalUrls.get(db), expectedDescriptors, indexPrefix);
    closables.add(migrator);
    closables.add(checks);
    getStore(db, context).put(MIGRATOR, migrator);
    getStore(db, context).put(DATABASE_TESTER, checks);
  }

  private void upgrade(
      final DatabaseType db,
      final ExtensionContext context,
      final Map<String, String> envOverrides) {
    final DatabaseType databaseType = getStore(db, context).get(DATABASE_TYPE, DatabaseType.class);
    final MigrationDatabaseChecks tester =
        getStore(db, context).get(DATABASE_TESTER, MigrationDatabaseChecks.class);
    awaitImportersFlushed(tester);

    final CamundaMigrator migrator = getStore(db, context).get(MIGRATOR, CamundaMigrator.class);
    migrator.update(databaseType, envOverrides);
    awaitExporterReadiness(tester);

    /* Ingest an 8.8 Record in order to trigger importers empty batch counting */
    ingestRecordToTriggerImporters(migrator.getCamundaClient());

    awaitImportersFinished(tester);
  }

  private boolean isNestedClass(final Class<?> currentClass) {
    return !ModifierSupport.isStatic(currentClass) && currentClass.isMemberClass();
  }

  private TestTemplateInvocationContext invocationContext(final DatabaseType databaseType) {
    return new TestTemplateInvocationContext() {

      private Object resolveParam(final Class<?> param, final ExtensionContext context) {
        final ExtensionContext.Store store = getStore(databaseType, context);
        if (param.equals(DatabaseType.class)) {
          return store.get(DATABASE_TYPE, DatabaseType.class);
        } else if (param.equals(CamundaMigrator.class)) {
          return store.get(MIGRATOR, CamundaMigrator.class);
        } else if (param.equals(CamundaClient.class)) {
          return store.get(MIGRATOR, CamundaMigrator.class).getCamundaClient();
        } else if (param.equals(DocumentBasedSearchClient.class)) {
          return store.get(MIGRATOR, CamundaMigrator.class).getSearchClient();
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
        closables.add(elasticsearchContainer);
        databaseExternalUrls.put(db, "http://" + elasticsearchContainer.getHttpHostAddress());
        break;
      case OPENSEARCH:
        final OpensearchContainer opensearchContainer =
            TestSearchContainers.createDefaultOpensearchContainer()
                .withNetwork(network)
                .withNetworkAliases("opensearch");
        opensearchContainer.start();
        databaseExternalUrls.put(db, opensearchContainer.getHttpHostAddress());
        closables.add(opensearchContainer);
        break;
      default:
        throw new IllegalArgumentException("Unsupported database type: " + db);
    }
  }

  private void awaitExporterReadiness(final MigrationDatabaseChecks tester) {
    Awaitility.await("Await database and exporter readiness")
        .timeout(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(tester::validateSchema);
  }

  private void awaitImportersFinished(final MigrationDatabaseChecks tester) {
    Awaitility.await("Await Importers finished")
        .timeout(Duration.ofMinutes(1))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () ->
                tester.checkImportersFinished(OPERATE) && tester.checkImportersFinished(TASKLIST));
  }

  private void awaitImportersFlushed(final MigrationDatabaseChecks tester) {
    Awaitility.await("Await Import Positions have been flushed")
        .timeout(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(500))
        .until(
            () ->
                tester.checkImportPositionsFlushed(OPERATE)
                    && tester.checkImportPositionsFlushed(TASKLIST));
  }

  private void ingestRecordToTriggerImporters(final CamundaClient client) {
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/error-end-event.bpmn")
        .send()
        .join();
  }

  private static ExtensionContext.Store getStore(
      final DatabaseType db, final ExtensionContext context) {
    if (db == DatabaseType.ELASTICSEARCH) {
      return context.getStore(ExtensionContext.Namespace.create(STORE_NAMESPACE));
    } else {
      return context.getStore(ExtensionContext.Namespace.create(STORE_NAMESPACE_OS));
    }
  }
}
