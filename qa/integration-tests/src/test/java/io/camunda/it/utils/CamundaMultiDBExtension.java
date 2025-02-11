/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.ModifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * An extension that is able to detect databases setups, configure {@link TestStandaloneApplication}
 * and run test against such them accordingly.
 *
 * <p>Databases can be set up externally. The detection works based on {@link
 * CamundaMultiDBExtension#PROP_CAMUNDA_IT_DATABASE_TYPE} property, which specifies the type of
 * database. Supported types can be found as part of {@link DatabaseType}.
 *
 * <p>Per default, for example if no property is set, local environment is expected. In a local
 * environment case the extension will bootstrap a database via test containers.
 *
 * <p>For simplicity tests can be annotated with {@link MultiDbTest}, and all the magic happens inside
 * the extension. It will fallback to {@link TestSimpleCamundaApplication}, to bootstrap a single
 * camunda application, configure it accordingly to the detected database.
 *
 * <pre>{@code
 * @MultiDbTest
 * final class MyMultiDbTest {
 *
 *   private CamundaClient client;
 *
 *   @Test
 *   void shouldMakeUseOfClient() {
 *     // given
 *     // ... set up
 *
 *     // when
 *     topology = c.newTopologyRequest().send().join();
 *
 *     // then
 *     assertThat(topology.getClusterSize()).isEqualTo(1);
 *   }
 * }</pre>
 *
 * <p>There are more complex scenarios that might need to start respective TestApplication externally.
 * For such cases the extension can be used via:
 * <pre>{@code
 * @RegisterExtension
 * public final CamundaMultiDBExtension extension =
 *    new CamundaMultiDBExtension(new TestStandaloneBroker());
 * }</pre>
 *
 *<p>The extension will take care of the life cycle of the {@link TestStandaloneApplication}, which
 * means configuring the detected database (this includes Operate, Tasklist, Broker properties and
 * exporter), starting the application, and tearing down at the end.
 *
 * <p>See {@link TestStandaloneApplication} for more details.
 */
public class CamundaMultiDBExtension
    implements AfterAllCallback, BeforeAllCallback, ParameterResolver {
  public static final String PROP_CAMUNDA_IT_DATABASE_TYPE =
      "test.integration.camunda.database.type";
  public static final String DEFAULT_ES_URL = "http://localhost:9200";
  public static final String DEFAULT_OS_URL = "http://localhost:9200";
  public static final String DEFAULT_OS_ADMIN_USER = "admin";
  public static final String DEFAULT_OS_ADMIN_PW = "yourStrongPassword123!";
  public static final Duration TIMEOUT_DATABASE_EXPORTER_READINESS = Duration.ofMinutes(1);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaMultiDBExtension.class);

  private final DatabaseType databaseType;
  private final List<AutoCloseable> closeables = new ArrayList<>();
  private final TestStandaloneApplication<?> testApplication;
  private String testPrefix;
  private final MultiDbConfigurator multiDbConfigurator;
  private ThrowingRunnable databaseSetupReadinessWaitStrategy = () -> {};
  private final HttpClient httpClient;

  public CamundaMultiDBExtension() {
    this(new TestStandaloneBroker());
    closeables.add(testApplication);
    testApplication
        .withBrokerConfig(cfg -> cfg.getGateway().setEnable(true))
        .withExporter(
            "recordingExporter", cfg -> cfg.setClassName(RecordingExporter.class.getName()));
  }

  public CamundaMultiDBExtension(final TestStandaloneApplication testApplication) {
    this.testApplication = testApplication;
    multiDbConfigurator = new MultiDbConfigurator(testApplication);
    // resolve active database and exporter type
    final String property = System.getProperty(PROP_CAMUNDA_IT_DATABASE_TYPE);
    databaseType =
        property == null ? DatabaseType.LOCAL : DatabaseType.valueOf(property.toUpperCase());
    httpClient = HttpClient.newHttpClient();
    closeables.add(httpClient);
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    LOGGER.info("Starting up Camunda instance, with {}", databaseType);
    final Class<?> testClass = context.getRequiredTestClass();
    testPrefix = testClass.getSimpleName().toLowerCase();

    switch (databaseType) {
      case LOCAL -> {
        final ElasticsearchContainer elasticsearchContainer = setupElasticsearch();
        final String elasticSearchUrl = "http://" + elasticsearchContainer.getHttpHostAddress();
        validateESConnection(elasticSearchUrl);
        multiDbConfigurator.configureElasticsearchSupport(elasticSearchUrl, testPrefix);
        final var expectedDescriptors = new IndexDescriptors(testPrefix, true).all();
        databaseSetupReadinessWaitStrategy =
            () -> validateSchemaCreation(elasticSearchUrl, testPrefix, expectedDescriptors);
      }
      case ES -> {
        validateESConnection(DEFAULT_ES_URL);
        multiDbConfigurator.configureElasticsearchSupport(DEFAULT_ES_URL, testPrefix);
        final var expectedDescriptors = new IndexDescriptors(testPrefix, true).all();
        databaseSetupReadinessWaitStrategy =
            () -> validateSchemaCreation(DEFAULT_ES_URL, testPrefix, expectedDescriptors);
      }
      case OS -> {
        validateESConnection(DEFAULT_OS_URL);
        multiDbConfigurator.configureOpenSearchSupport(
            DEFAULT_OS_URL, testPrefix, DEFAULT_OS_ADMIN_USER, DEFAULT_OS_ADMIN_PW);
        final var expectedDescriptors = new IndexDescriptors(testPrefix, true).all();
        databaseSetupReadinessWaitStrategy =
            () -> validateSchemaCreation(DEFAULT_ES_URL, testPrefix, expectedDescriptors);
      }
      case RDBMS -> multiDbConfigurator.configureRDBMSSupport();
      default -> throw new RuntimeException("Unknown exporter type");
    }
    testApplication.start();
    testApplication.awaitCompleteTopology();

    Awaitility.await("Await database and exporter readiness")
        .timeout(TIMEOUT_DATABASE_EXPORTER_READINESS)
        .untilAsserted(databaseSetupReadinessWaitStrategy);

    injectFields(testClass, null, ModifierSupport::isStatic);
  }

  private ElasticsearchContainer setupElasticsearch() {
    final ElasticsearchContainer elasticsearchContainer =
        TestSearchContainers.createDefeaultElasticsearchContainer();
    elasticsearchContainer.start();
    closeables.add(elasticsearchContainer);
    return elasticsearchContainer;
  }

  /**
   * Validate the schema creation. Expects harmonized indices to be created. Optimize indices and ES
   * Exporter are not included.
   *
   * <p>ES exporter indices are only created, on first exporting, so we expect at least this amount
   * or more (to fight race conditions).
   *
   * @param url the url to get actual indices
   * @param testPrefix the test prefix of the actual indices
   * @param expectedDescriptors expected descriptors
   */
  private void validateSchemaCreation(
      final String url,
      final String testPrefix,
      final Collection<IndexDescriptor> expectedDescriptors) {
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
      final JsonNode jsonNode = OBJECT_MAPPER.readTree(response.body());
      final int count = jsonNode.size();
      final boolean reachedCount = expectedDescriptors.size() <= count;
      if (reachedCount) {
        // Expected indices reached
        return;
      }

      LOGGER.debug(
          "[{}/{}] indices with prefix {} in ES, retry...",
          count,
          expectedDescriptors.size(),
          testPrefix);

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

  private static void validateESConnection(final String url) {
    final HttpRequest httpRequest =
        HttpRequest.newBuilder().GET().uri(URI.create(String.format("%s/", url))).build();
    try (final HttpClient httpClient = HttpClient.newHttpClient()) {
      final HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());
      final int statusCode = response.statusCode();
      assert statusCode / 100 == 2
          : "Expected to have a running ES service available under: " + url;
    } catch (final IOException | InterruptedException e) {
      assert false
          : "Expected no exception on validating connection under: "
              + url
              + ", failed with: "
              + e
              + ": "
              + e.getMessage();
    }
  }

  private void injectFields(
      final Class<?> testClass, final Object testInstance, Predicate<Field> predicate) {
    predicate = predicate.and(field -> field.getType() == CamundaClient.class);
    for (final Field field : testClass.getDeclaredFields()) {
      try {
        if (predicate.test(field)) {
          field.setAccessible(true);
          field.set(testInstance, createCamundaClient());
        }
      } catch (final Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  private void withRetry(final Callable<Boolean> operation, final int maxAttempt) {
    int attempt = 0;
    boolean shouldRetry = true;
    while (shouldRetry) {
      try {
        // if we succeed we don't want to retry
        shouldRetry = !operation.call();
      } catch (final Exception ex) {
        LOGGER.debug(
            "Failed to execute {}. Attempts: [{}/{}]", operation, attempt + 1, maxAttempt, ex);
      } finally {
        // if we reached the max attempt we stop
        if (++attempt >= maxAttempt) {
          shouldRetry = false;
        }
      }

      if (shouldRetry) {
        try {
          // wait a little between retries
          Thread.sleep(100);
        } catch (final InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  @Override
  public void afterAll(final ExtensionContext context) {
    if (databaseType == DatabaseType.ES || databaseType == DatabaseType.OS) {
      try (final HttpClient httpClient = HttpClient.newHttpClient()) {

        // delete indices
        // https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-delete-index.html
        withRetry(
            () -> {
              final URI deleteIndicesEndpoint =
                  URI.create(String.format("%s/%s-*", DEFAULT_ES_URL, testPrefix));
              return sendHttpDeleteRequest(httpClient, deleteIndicesEndpoint);
            },
            5);

        // Deleting index templates are separate from deleting indices, and we need to make sure
        // that we also get rid of the template, so we can properly recreate them
        // https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-delete-template.html
        //
        // See related CI incident https://github.com/camunda/camunda/pull/27985
        withRetry(
            () -> {
              final URI deleteIndexTemplatesEndpoint =
                  URI.create(String.format("%s/_index_template/%s-*", DEFAULT_ES_URL, testPrefix));
              return sendHttpDeleteRequest(httpClient, deleteIndexTemplatesEndpoint);
            },
            5);
      }
    }
    CloseHelper.quietCloseAll(closeables);
  }

  @NotNull
  private Boolean sendHttpDeleteRequest(final HttpClient httpClient, final URI deleteEndpoint)
      throws IOException, InterruptedException {
    final var httpRequest = HttpRequest.newBuilder().DELETE().uri(deleteEndpoint).build();
    final var response = httpClient.send(httpRequest, BodyHandlers.ofString());
    final var statusCode = response.statusCode();
    if (statusCode / 100 == 2) {
      LOGGER.info("Deletion on {} was successful", deleteEndpoint.toString());
      return true;
    } else {
      LOGGER.warn(
          "Failure on deletion at {}. Status code: {} [{}]",
          deleteEndpoint.toString(),
          statusCode,
          response.body());
    }
    return false;
  }

  @Override
  public boolean supportsParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return parameterContext.getParameter().getType() == CamundaClient.class;
  }

  @Override
  public Object resolveParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return createCamundaClient();
  }

  private CamundaClient createCamundaClient() {
    final CamundaClient camundaClient = testApplication.newClientBuilder().build();
    closeables.add(camundaClient);
    return camundaClient;
  }

  public enum DatabaseType {
    LOCAL,
    ES,
    OS,
    RDBMS
  }
}
