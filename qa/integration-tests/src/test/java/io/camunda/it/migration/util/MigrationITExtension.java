/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import io.camunda.client.CamundaClient;
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
  private static final Map<DatabaseType, MigrationHelper> helpers = new HashMap<>();
  private static final List<AutoCloseable> dbClosables = new ArrayList<>();
  private static final Map<DatabaseType, Network> networks = new HashMap<>();
  private static final Set<DatabaseType> databaseTypes =
      Set.of(/*DatabaseType.ELASTICSEARCH,*/ DatabaseType.OPENSEARCH);
  private static final Map<DatabaseType, String> databaseExternalUrls = new HashMap<>();
  private static final Map<DatabaseType, MigrationDatabaseChecks> databaseChecks = new HashMap<>();
  private static final Map<DatabaseType, Boolean> has87Data =
      new HashMap<>() {
        {
          put(DatabaseType.ELASTICSEARCH, false);
          put(DatabaseType.OPENSEARCH, false);
        }
      };
  private static final Map<DatabaseType, String> indexPrefixes = new HashMap<>();

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
    final String indexPrefix = context.getTestMethod().get().getName().toLowerCase();

    final DatabaseType db = DatabaseType.valueOf(context.getDisplayName());
    try {
      helpers.get(db).close();
      helpers.get(db).cleanup();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    databaseChecks.get(db).cleanup(indexPrefix);
    databaseChecks.get(db).close();
    indexPrefixes.remove(db);
    has87Data.put(db, false);
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
              indexPrefixes.put(db, indexPrefix);
              context.getTags().add(db.name());
              final MigrationHelper helper = new MigrationHelper(networks.get(db), indexPrefix);
              helper.initialize(db, databaseExternalUrls.get(db));
              final var expectedDescriptors =
                  new IndexDescriptors(indexPrefix, db.equals(DatabaseType.ELASTICSEARCH)).all();
              databaseChecks.put(
                  db,
                  new MigrationDatabaseChecks(databaseExternalUrls.get(db), expectedDescriptors));
              helpers.put(db, helper);
              return Stream.of(invocationContext(db));
            });
  }

  public void upgrade(final DatabaseType databaseType) {
    final String indexPrefix = indexPrefixes.get(databaseType);
    if (has87Data.get(databaseType)) {
      Awaitility.await("Await Import Positions have been flushed")
          .timeout(Duration.of(1, ChronoUnit.MINUTES))
          .pollInterval(Duration.of(500, ChronoUnit.MILLIS))
          .until(
              () ->
                  databaseChecks.get(databaseType).checkImportPositionsFlushed(indexPrefix, OPERATE)
                      && databaseChecks
                          .get(databaseType)
                          .checkImportPositionsFlushed(indexPrefix, TASKLIST));
    }

    helpers.get(databaseType).update(databaseType);

    Awaitility.await("Await database and exporter readiness")
        .timeout(Duration.of(60, ChronoUnit.SECONDS))
        .pollInterval(Duration.of(500, ChronoUnit.MILLIS))
        .untilAsserted(() -> databaseChecks.get(databaseType).validateSchemaCreation(indexPrefix));

    /* Ingest an 8.8 Record in order to trigger importers empty batch counting */
    ingestRecordToTriggerImporters(databaseType);

    Awaitility.await("Await Importers finished")
        .timeout(Duration.of(2, ChronoUnit.MINUTES))
        .pollInterval(Duration.of(500, ChronoUnit.MILLIS))
        .until(
            () ->
                databaseChecks.get(databaseType).checkImportersFinished(indexPrefix, OPERATE)
                    && databaseChecks
                        .get(databaseType)
                        .checkImportersFinished(indexPrefix, TASKLIST));
  }

  private void ingestRecordToTriggerImporters(final DatabaseType databaseType) {
    getCamundaClient(databaseType)
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/error-end-event.bpmn")
        .send()
        .join();
  }

  private boolean isNestedClass(final Class<?> currentClass) {
    return !ModifierSupport.isStatic(currentClass) && currentClass.isMemberClass();
  }

  public CamundaClient getCamundaClient(final DatabaseType databaseType) {
    return helpers.get(databaseType).getCamundaClient();
  }

  /**
   * If the test case creates data on 8.7 version, it should be marked as such in order to wait for
   * the Tasklist & Operate import-position indices to be flushed
   */
  public void has87Data(final DatabaseType databaseType) {
    has87Data.put(databaseType, true);
  }

  public MigrationHelper helper(final DatabaseType databaseType) {
    return helpers.get(databaseType);
  }

  private TestTemplateInvocationContext invocationContext(final DatabaseType databaseType) {
    return new TestTemplateInvocationContext() {

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
                return Set.of(DatabaseType.class, CamundaClient.class, MigrationHelper.class)
                    .contains(parameterContext.getParameter().getType());
              }

              @Override
              public Object resolveParameter(
                  final ParameterContext parameterContext, final ExtensionContext extensionContext)
                  throws ParameterResolutionException {
                if (parameterContext.getParameter().getType().equals(CamundaClient.class)) {
                  return helpers.get(databaseType).getCamundaClient();
                } else if (parameterContext
                    .getParameter()
                    .getType()
                    .equals(MigrationHelper.class)) {
                  return helpers.get(databaseType);
                } else if (parameterContext.getParameter().getType().equals(DatabaseType.class)) {
                  return databaseType;
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
        elasticsearchContainer.setPortBindings(List.of("9200:9200"));
        elasticsearchContainer.start();
        dbClosables.add(elasticsearchContainer);
        databaseExternalUrls.put(db, "http://" + elasticsearchContainer.getHttpHostAddress());
        break;
      case OPENSEARCH:
        final OpensearchContainer opensearchContainer =
            TestSearchContainers.createDefaultOpensearchContainer()
                .withNetwork(network)
                .withNetworkAliases("opensearch");
        opensearchContainer.setPortBindings(List.of("9210:9200"));
        opensearchContainer.start();
        databaseExternalUrls.put(db, opensearchContainer.getHttpHostAddress());
        dbClosables.add(opensearchContainer);
        break;
      default:
        throw new IllegalArgumentException("Unsupported database type: " + db);
    }
  }
}
