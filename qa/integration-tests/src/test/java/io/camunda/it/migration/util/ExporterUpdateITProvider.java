/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.reindex.Destination;
import co.elastic.clients.elasticsearch.core.reindex.Source;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.ModifierSupport;
import org.opensearch.testcontainers.OpensearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class ExporterUpdateITProvider
    implements AfterAllCallback,
        BeforeAllCallback,
        AfterEachCallback,
        TestTemplateInvocationContextProvider,
        InvocationInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExporterUpdateITProvider.class);
  private static final Map<DatabaseType, Map<Component, AbstractComponentHelper<?>>> dependencies =
      new HashMap<>();
  private static final List<AutoCloseable> dbClosables = new ArrayList<>();
  private static final Map<DatabaseType, Network> networks = new HashMap<>();
  private static final Set<DatabaseType> databaseTypes =
      Set.of(DatabaseType.ELASTICSEARCH, DatabaseType.OPENSEARCH);
  private static final Map<DatabaseType, String> databaseExternalUrls = new HashMap<>();
  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create("test");
  private String indexPrefix;

  @Override
  public void afterAll(final ExtensionContext context) {
    cleanUp();
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

  private void cleanUp() {
    dependencies.entrySet().parallelStream()
        .forEach(
            e -> {
              e.getValue().values().parallelStream()
                  .forEach(
                      helper -> {
                        try {
                          helper.close();
                          if (helper instanceof ZeebeComponentHelper) {
                            ((ZeebeComponentHelper) helper).cleanup();
                          }
                        } catch (final Exception ex) {
                          throw new RuntimeException(ex);
                        }
                      });
            });
  }

  @Override
  public void afterEach(final ExtensionContext context) {
    cleanUp();
    final String indexPrefx = context.getTestMethod().get().getName().toLowerCase();

    final DatabaseType db =
        context
            .getStore(NAMESPACE)
            .getOrDefault(
                indexPrefix + "-" + DatabaseType.OPENSEARCH.name(),
                DatabaseType.class,
                DatabaseType.ELASTICSEARCH);

    context.getStore(NAMESPACE).remove(indexPrefix + "-" + db.name());
    final URI deleteEndpoint =
        URI.create(String.format("%s/%s*", databaseExternalUrls.get(db), indexPrefx));
    final HttpRequest httpRequest = HttpRequest.newBuilder().DELETE().uri(deleteEndpoint).build();
    try (final HttpClient httpClient = HttpClient.newHttpClient()) {
      final HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());
      final int statusCode = response.statusCode();
      if (statusCode / 100 == 2) {
        LOGGER.info("Test data for prefix {} deleted.", indexPrefx);
      } else {
        LOGGER.warn(
            "Failure on deleting test data for prefix {}. Status code: {} [{}]",
            indexPrefx,
            statusCode,
            response.body());
      }
    } catch (final IOException | InterruptedException e) {
      LOGGER.warn("Failure on deleting test data.", e);
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

  private boolean isNestedClass(final Class<?> currentClass) {
    return !ModifierSupport.isStatic(currentClass) && currentClass.isMemberClass();
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
        opensearchContainer.start();
        databaseExternalUrls.put(db, opensearchContainer.getHttpHostAddress());
        dbClosables.add(opensearchContainer);
        break;
      default:
        throw new IllegalArgumentException("Unsupported database type: " + db);
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
              indexPrefix = context.getTestMethod().get().getName().toLowerCase();
              context.getStore(NAMESPACE).put(indexPrefix + "-" + db.name(), db);
              dependencies.put(db, new HashMap<>());
              final var zb = new ZeebeComponentHelper(networks.get(db), indexPrefix);
              zb.initial(db, Map.of("camunda.database.url", databaseExternalUrls.get(db)));
              dependencies.get(db).put(Component.ZEEBE, zb);
              return Stream.of(invocationContext(db));
            });
  }

  public void upgradeComponent(final Component component, final DatabaseType databaseType) {
    stopComponent(component, databaseType);
    dependencies
        .get(databaseType)
        .get(component)
        .update(
            databaseType, Map.of("camunda.database.url", databaseExternalUrls.get(databaseType)));

    final var expectedDescriptors = new IndexDescriptors(indexPrefix, true).all();
    Awaitility.await("Await database and exporter readiness")
        .timeout(Duration.of(60, ChronoUnit.SECONDS))
        .untilAsserted(
            () ->
                waitForIndices(
                    databaseExternalUrls.get(databaseType), indexPrefix, expectedDescriptors));
    /* TODO: Temporary until Operate update is functional */
    if (component.equals(Component.ZEEBE)) {
      try {
        cloneProcesses(databaseType);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void stopComponent(final Component component, final DatabaseType databaseType) {
    try {
      dependencies.get(databaseType).get(component).close();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public CamundaClient getCamundaClient(final DatabaseType databaseType) {
    return ((ZeebeComponentHelper) dependencies.get(databaseType).get(Component.ZEEBE))
        .getCamundaClient();
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
                return Set.of(
                        DatabaseType.class,
                        CamundaClient.class,
                        TasklistComponentHelper.class,
                        OperateComponentHelper.class)
                    .contains(parameterContext.getParameter().getType());
              }

              /*
               * Apart from Zeebe, other components are initialized if injected in the test context
               */
              @Override
              public Object resolveParameter(
                  final ParameterContext parameterContext, final ExtensionContext extensionContext)
                  throws ParameterResolutionException {
                // Zeebe is instantiated always
                final var zb =
                    (ZeebeComponentHelper) dependencies.get(databaseType).get(Component.ZEEBE);
                if (parameterContext.getParameter().getType().equals(CamundaClient.class)) {
                  return ((ZeebeComponentHelper)
                          dependencies.get(databaseType).get(Component.ZEEBE))
                      .getCamundaClient();
                } else if (parameterContext
                    .getParameter()
                    .getType()
                    .equals(TasklistComponentHelper.class)) {
                  final var tasklist =
                      new TasklistComponentHelper(zb, networks.get(databaseType), indexPrefix);
                  dependencies.get(databaseType).put(Component.TASKLIST, tasklist);
                  tasklist.initial(databaseType);
                  return tasklist;
                } else if (parameterContext
                    .getParameter()
                    .getType()
                    .equals(OperateComponentHelper.class)) {
                  final var operate =
                      new OperateComponentHelper(zb, networks.get(databaseType), indexPrefix);
                  dependencies.get(databaseType).put(Component.OPERATE, operate);
                  operate.initial(databaseType);
                  return operate;
                } else if (parameterContext.getParameter().getType().equals(DatabaseType.class)) {
                  return databaseType;
                }
                throw new ParameterResolutionException("Unsupported parameter type");
              }
            });
      }
    };
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
                            .get(indexPrefix + "-operate-process-8.3.0_")
                        != null);

        // Copy previous tasklist-process to operate-process, required for V2 APIs
        esClient.reindex(
            r ->
                r.source(Source.of(s -> s.index(indexPrefix + "-tasklist-process-8.4.0_")))
                    .dest(Destination.of(d -> d.index(indexPrefix + "-operate-process-8.3.0_")))
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
                            .get(indexPrefix + "-operate-process-8.3.0_")
                        != null);

        // Copy previous tasklist-process to operate-process, required for V2 APIs
        osClient.reindex(
            r ->
                r.source(
                        org.opensearch.client.opensearch.core.reindex.Source.of(
                            s -> s.index(indexPrefix + "-tasklist-process-8.4.0_")))
                    .dest(
                        org.opensearch.client.opensearch.core.reindex.Destination.of(
                            d -> d.index(indexPrefix + "-operate-process-8.3.0_")))
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

  /* Taken from MultiDBTest implementation */
  private void waitForIndices(
      final String url,
      final String testPrefix,
      final Collection<IndexDescriptor> expectedDescriptors) {
    final var httpClient = java.net.http.HttpClient.newHttpClient();
    final HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(String.format("%s/%s*", url, testPrefix)))
            .build();
    try {
      final HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());
      final int statusCode = response.statusCode();
      assertThat(statusCode).isBetween(200, 299);

      // Get how many indices with given prefix we have
      final JsonNode jsonNode = new ObjectMapper().readTree(response.body());
      final int count = jsonNode.size();
      final boolean reachedCount = expectedDescriptors.size() <= count;
      if (reachedCount) {
        // Expected indices reached
        return;
      }

      final Iterator<String> stringIterator = jsonNode.fieldNames();
      final Iterable<String> iterable = () -> stringIterator;
      final List<String> actualIndices =
          StreamSupport.stream(iterable.spliterator(), false).toList();

      final var expectedIndexNames =
          expectedDescriptors.stream().map(IndexDescriptor::getFullQualifiedName).toList();
      assertThat(actualIndices).as("Missing indices").containsAll(expectedIndexNames);
    } catch (final IOException | InterruptedException e) {
      fail(
          "Expected no exception on validating connection under: "
              + url
              + ", failed with: "
              + e
              + ": "
              + e.getMessage(),
          e);
    }
  }

  public enum Component {
    ZEEBE,
    TASKLIST,
    OPERATE
  }
}
