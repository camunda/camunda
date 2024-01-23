/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.zeebe.exporter.TestClient;
import io.camunda.zeebe.exporter.TestSupport;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public abstract class AbstractOperateElasticsearchExporterIT {

  @Container
  private static final ElasticsearchContainer CONTAINER = TestSupport.createDefaultContainer();

  protected final ProtocolFactory factory = new ProtocolFactory();
  protected OperateElasticsearchExporter exporter;
  protected OperateElasticsearchManager schemaManager;
  protected ExportBatchWriter writer;

  private final OperateElasticsearchExporterConfiguration config =
      new OperateElasticsearchExporterConfiguration();

  private ExporterTestController controller;
  private TestClient testClient;

  private RestHighLevelClient esClient;

  @BeforeAll
  public void beforeAll() {
    config.url = CONTAINER.getHttpHostAddress();
    // TODO: who is creating the indexes? exporter or operate? how do we want to do it in the test
    // then?
    // config.index.setNumberOfShards(1);
    // config.index.setNumberOfReplicas(1);
    // config.index.createTemplate = true;
    config.getBulk().size = 1; // force flushing on the first record

    configureExporter(config);
  }

  protected void configureExporter(OperateElasticsearchExporterConfiguration configuration) {
    // no op by default
  }

  @BeforeEach
  public void setUpExporter() throws Exception {
    controller = new ExporterTestController();

    exporter = new OperateElasticsearchExporter();

    exporter.configure(
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", config)));
    exporter.open(controller);
    writer = exporter.getWriter();

    esClient = exporter.createEsClient();
    schemaManager = exporter.getSchemaManager();
  }

  @AfterEach
  public void tearDownExporter() {
    exporter.close();
    exporter = null;
    controller = null;
    writer = null;
  }

  protected OffsetDateTime asDate(long time) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
  }

  protected List<Record<?>> readRecordsFromJsonResource(String resourceName) {
    final List<Record<?>> result = new ArrayList<>();

    try {

      final ObjectMapper objectMapper =
          new ObjectMapper().registerModule(new ZeebeProtocolModule());
      try (InputStream inputStream =
          AbstractOperateElasticsearchExporterIT.class
              .getClassLoader()
              .getResourceAsStream(resourceName)) {
        final List<String> lines = IOUtils.readLines(inputStream, StandardCharsets.UTF_8);

        for (String jsonString : lines) {
          final Record<?> record =
              objectMapper.readValue(jsonString, new TypeReference<Record<?>>() {});
          result.add(record);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(
          String.format("Could not read records from classpath resource %s", resourceName), e);
    }

    return result;
  }

  protected void assertDocument(
      final String expectedId, final String indexName, String handlerName) {
    final Map<String, Object> document = findElasticsearchDocument(indexName, expectedId);
    assertThat(document).isNotNull();
    System.out.println(String.format("Returned document %s", document));
  }

  protected Map<String, Object> findElasticsearchDocument(String index, String id) {

    final List<Map<String, Object>> results =
        findElasticsearchDocuments(index, QueryBuilders.idsQuery().addIds(id));

    if (results.size() == 1) {
      return results.get(0);
    } else {
      throw new RuntimeException(
          String.format(
              "Could not find a single document with id %s in index %s; got %s results",
              id, index, results.size()));
    }
  }

  protected List<Map<String, Object>> findElasticsearchDocuments(String index, QueryBuilder query) {

    try {
      schemaManager.refresh(index); // ensure latest data is visible
      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder().query(query).size(10000);
      final SearchRequest searchRequest =
          new SearchRequest().indices(index).source(searchSourceBuilder);
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      final SearchHits searchHits = response.getHits();

      final List<Map<String, Object>> result = new ArrayList<>();
      searchHits.forEach(
          hit -> {
            result.add(hit.getSourceAsMap());
          });

      return result;

    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  protected <T extends OperateEntity<T>> Map<String, T> getMatchingDocuments(
      String index, Class<T> entityClass, QueryBuilder query) {
    try {
      schemaManager.refresh(index); // ensure latest data is visible

      final SearchRequest request =
          new SearchRequest(index).source(new SearchSourceBuilder().query(query));
      final List<T> searchResults =
          ElasticsearchUtil.scroll(
              request, entityClass, NoSpringJacksonConfig.buildObjectMapper(), esClient);

      final Map<String, T> result = new HashMap<>();
      searchResults.forEach(entity -> result.put(entity.getId(), entity));

      return result;

    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
