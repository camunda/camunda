/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.operate.Metrics;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.conditions.DatabaseType;
import io.camunda.operate.connect.CustomOffsetDateTimeSerializer;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.property.ArchiverProperties;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.templates.AbstractTemplateDescriptor;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ArchiverJobIT.TestConfig.class)
@TestInstance(Lifecycle.PER_CLASS)
public abstract class ArchiverJobIT {

  protected static final int PARTITION_ID = 1;
  protected static final List<Integer> PARTITION_IDS = List.of(PARTITION_ID);
  protected static final AtomicLong ID_GENERATOR = new AtomicLong(0);
  protected static final String INDEX_PREFIX = "test-archiver";
  protected static final Duration ARCHIVE_TIMEOUT = Duration.ofSeconds(30);

  private static final String OPERATE_TEST_DB_PROPERTY = "OPERATE_TEST_DB";

  private static final ElasticsearchContainer ELASTICSEARCH =
      new ElasticsearchContainer(
              "docker.elastic.co/elasticsearch/elasticsearch:"
                  + RestHighLevelClient.class.getPackage().getImplementationVersion())
          .withEnv("xpack.security.enabled", "false");

  @SuppressWarnings("resource")
  private static final OpenSearchContainer<?> OPENSEARCH =
      new OpenSearchContainer<>("opensearchproject/opensearch:2.11.1")
          .withEnv("DISABLE_SECURITY_PLUGIN", "true");

  @Autowired protected OperateProperties operateProperties;
  @Autowired protected ObjectMapper objectMapper;
  protected SearchClientAdapter searchClient;
  @Autowired private Metrics metrics;
  private ArchiverRepository archiverRepository;
  private Archiver archiver;

  @Autowired private ListViewTemplate processInstanceTemplate;
  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;
  @Autowired private List<TemplateDescriptor> allTemplateDescriptors;
  @Autowired private List<AbstractIndexDescriptor> allIndexDescriptors;
  private ElasticsearchSearchClientAdapter esAdapter;
  private OpensearchSearchClientAdapter osAdapter;

  @BeforeAll
  void setupInfrastructure() throws Exception {
    if (isOpensearch()) {
      ReflectionTestUtils.setField(DatabaseInfo.class, "current", DatabaseType.Opensearch);
      OPENSEARCH.start();
      osAdapter = new OpensearchSearchClientAdapter(OPENSEARCH, objectMapper);
      searchClient = osAdapter;
      archiverRepository = buildOpensearchArchiverRepository(osAdapter);
    } else {
      ReflectionTestUtils.setField(DatabaseInfo.class, "current", DatabaseType.Elasticsearch);
      ELASTICSEARCH.start();
      esAdapter = new ElasticsearchSearchClientAdapter(ELASTICSEARCH, objectMapper);
      searchClient = esAdapter;
      archiverRepository = buildElasticsearchArchiverRepository(esAdapter);
    }
    archiver =
        new Archiver(
            null,
            operateProperties,
            null,
            null,
            archiverRepository,
            processInstanceTemplate,
            List.of(),
            decisionInstanceTemplate,
            batchOperationTemplate,
            metrics);
  }

  @AfterEach
  void cleanupIndices() throws IOException {
    searchClient.deleteIndices(INDEX_PREFIX + "-*");
  }

  @Test
  void shouldExecuteWithoutErrorsWhenNothingToArchive() throws Exception {
    withArchiverJob(
        job -> {
          assertThat(job.archiveNextBatch()).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(0);
        });
  }

  protected void withArchiverJob(final ArchiverJobConsumer consumer) throws Exception {
    createSchema();
    final var job = createArchiveJob(PARTITION_IDS);
    consumer.accept(job);
  }

  protected abstract ArchiverJob createArchiveJob(List<Integer> partitionIds);

  protected void createSchema() throws Exception {
    buildSchemaManager().createSchema();
  }

  protected SchemaManager buildSchemaManager() {
    if (osAdapter != null) {
      return osAdapter.buildSchemaManager(
          operateProperties, allTemplateDescriptors, allIndexDescriptors);
    }
    return esAdapter.buildSchemaManager(
        operateProperties, allTemplateDescriptors, allIndexDescriptors);
  }

  protected Metrics getMetrics() {
    return metrics;
  }

  protected ArchiverRepository getArchiverRepository() {
    return archiverRepository;
  }

  protected Archiver getArchiver() {
    return archiver;
  }

  protected <E extends OperateEntity<E>> E create(final Supplier<E> constructor) {
    final long id = ID_GENERATOR.incrementAndGet();
    final var entity = constructor.get();
    entity.setId(String.valueOf(id));
    return entity;
  }

  protected void store(final AbstractTemplateDescriptor template, final OperateEntity<?> entity)
      throws IOException {
    searchClient.index(entity.getId(), null, template.getFullQualifiedName(), entity);
  }

  protected void store(
      final AbstractTemplateDescriptor template,
      final OperateEntity<?> entity,
      final String routing)
      throws IOException {
    searchClient.index(entity.getId(), routing, template.getFullQualifiedName(), entity);
  }

  protected void refresh() throws IOException {
    searchClient.refresh();
  }

  protected void verifyMoved(
      final AbstractTemplateDescriptor template, final String entityId, final String datedSuffix)
      throws IOException {
    verifyMoved(template, entityId, null, datedSuffix);
  }

  protected void verifyMoved(
      final AbstractTemplateDescriptor template,
      final String entityId,
      final String routing,
      final String datedSuffix)
      throws IOException {
    final String sourceIndex = template.getFullQualifiedName();
    final String destIndex = sourceIndex + datedSuffix;

    assertThat(searchClient.exists(entityId, routing, sourceIndex))
        .describedAs("Expected %s to have been deleted from source index %s", entityId, sourceIndex)
        .isFalse();
    assertThat(searchClient.exists(entityId, routing, destIndex))
        .describedAs("Expected %s to exist in dated index %s", entityId, destIndex)
        .isTrue();
  }

  protected void verifyNotMoved(final AbstractTemplateDescriptor template, final String entityId)
      throws IOException {
    verifyNotMoved(template, entityId, null);
  }

  protected void verifyNotMoved(
      final AbstractTemplateDescriptor template, final String entityId, final String routing)
      throws IOException {
    final String sourceIndex = template.getFullQualifiedName();
    assertThat(searchClient.exists(entityId, routing, sourceIndex))
        .describedAs("Expected %s to still be in source index %s", entityId, sourceIndex)
        .isTrue();
  }

  private boolean isOpensearch() {
    return "opensearch".equalsIgnoreCase(System.getProperty(OPERATE_TEST_DB_PROPERTY));
  }

  private ElasticsearchArchiverRepository buildElasticsearchArchiverRepository(
      final ElasticsearchSearchClientAdapter adapter) {
    return new ElasticsearchArchiverRepository(
        buildScheduler(),
        operateProperties,
        metrics,
        adapter.getClient(),
        processInstanceTemplate,
        batchOperationTemplate,
        decisionInstanceTemplate);
  }

  private OpensearchArchiverRepository buildOpensearchArchiverRepository(
      final OpensearchSearchClientAdapter adapter) {
    final var asyncClient = new OpenSearchAsyncClient(adapter.getClient()._transport());
    // beanFactory is null: only used by RichOpenSearchClient.batch().doWithRetry(), which the
    // archiver never calls — it uses osAsyncClient for searches and index() for existence checks.
    final var richClient =
        new RichOpenSearchClient(null, adapter.getClient(), asyncClient, objectMapper);
    return new OpensearchArchiverRepository(
        richClient,
        asyncClient,
        operateProperties,
        metrics,
        processInstanceTemplate,
        batchOperationTemplate,
        decisionInstanceTemplate,
        buildScheduler());
  }

  protected static ThreadPoolTaskScheduler buildScheduler() {
    final var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("archiver-test-");
    scheduler.setDaemon(true);
    scheduler.initialize();
    return scheduler;
  }

  @Configuration
  @ComponentScan({"io.camunda.operate.schema.templates", "io.camunda.operate.schema.indices"})
  static class TestConfig {

    @Bean
    OperateProperties operateProperties() {
      final var props = new OperateProperties();

      final var esProps = new OperateElasticsearchProperties();
      esProps.setIndexPrefix(INDEX_PREFIX);
      props.setElasticsearch(esProps);

      final var osProps = new OperateOpensearchProperties();
      osProps.setIndexPrefix(INDEX_PREFIX);
      props.setOpensearch(osProps);

      final var archiverProps = new ArchiverProperties();
      archiverProps.setRolloverInterval("1d");
      archiverProps.setElsRolloverDateFormat("date");
      archiverProps.setRolloverBatchSize(100);
      archiverProps.setIlmEnabled(false);
      props.setArchiver(archiverProps);

      return props;
    }

    @Bean
    Metrics metrics() {
      return new Metrics(new SimpleMeterRegistry());
    }

    @Bean
    ObjectMapper objectMapper() {
      final var javaTimeModule = new JavaTimeModule();
      final var formatter =
          DateTimeFormatter.ofPattern(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);
      javaTimeModule.addSerializer(
          OffsetDateTime.class, new CustomOffsetDateTimeSerializer(formatter));
      return new ObjectMapper()
          .registerModule(javaTimeModule)
          .registerModule(new Jdk8Module())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .enable(JsonParser.Feature.ALLOW_COMMENTS)
          .setVisibility(PropertyAccessor.GETTER, Visibility.ANY)
          .setVisibility(PropertyAccessor.IS_GETTER, Visibility.ANY)
          .setVisibility(PropertyAccessor.SETTER, Visibility.ANY)
          .setVisibility(PropertyAccessor.FIELD, Visibility.NONE)
          .setVisibility(PropertyAccessor.CREATOR, Visibility.ANY);
    }
  }

  @FunctionalInterface
  protected interface ArchiverJobConsumer {
    void accept(ArchiverJob job) throws Exception;
  }

  protected interface SearchClientAdapter<T> {
    T getClient();

    void index(String id, String routing, String indexName, Object entity) throws IOException;

    boolean exists(String id, String routing, String indexName) throws IOException;

    void refresh() throws IOException;

    void deleteIndices(String pattern) throws IOException;
  }
}
