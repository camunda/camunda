/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.migration.performance;

import static io.camunda.operate.util.CollectionUtil.filter;
import static io.camunda.operate.util.CollectionUtil.map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.entities.*;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.*;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ComponentScan(
    basePackages = {
      "io.camunda.operate.property",
      "io.camunda.operate.connect",
      "io.camunda.operate.schema.templates",
      "io.camunda.operate.schema.indices",
      "io.camunda.operate.conditions"
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Import(JacksonConfig.class)
public class DataMultiplicator implements CommandLineRunner {

  public static final int MAX_DOCUMENTS = 10_000;
  private static final Logger LOGGER = LoggerFactory.getLogger(DataMultiplicator.class);
  @Autowired private RestHighLevelClient esClient;
  @Autowired private OperateProperties operateProperties;
  @Autowired private List<TemplateDescriptor> indexDescriptors;
  private final Map<Class<? extends TemplateDescriptor>, Class<? extends OperateEntity>>
      descriptorToEntity =
          Map.of(
              EventTemplate.class, EventEntity.class,
              SequenceFlowTemplate.class, SequenceFlowEntity.class,
              VariableTemplate.class, VariableEntity.class,
              IncidentTemplate.class, IncidentEntity.class);
  @Autowired private ObjectMapper objectMapper;

  public static void main(final String[] args) {
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    final SpringApplication springApplication = new SpringApplication(DataMultiplicator.class);
    springApplication.setWebApplicationType(WebApplicationType.NONE);
    springApplication.setAddCommandLineProperties(true);
    final ConfigurableApplicationContext ctx = springApplication.run(args);
    SpringApplication.exit(ctx);
  }

  @Override
  public void run(final String... args) throws Exception {
    final int[] times = {500};
    try {
      times[0] = Integer.parseInt(args[0]);
    } catch (final Exception e) {
      LOGGER.warn("Couldn't parse times of duplication. Use default {}", times[0]);
    }
    final List<TemplateDescriptor> duplicatable =
        filter(
            indexDescriptors, descriptor -> descriptorToEntity.containsKey(descriptor.getClass()));
    final List<Thread> duplicators =
        map(duplicatable, descriptor -> new Thread(() -> duplicateIndexBy(times[0], descriptor)));
    duplicators.forEach(Thread::start);
    for (final Thread t : duplicators) {
      t.join();
    }
  }

  private void duplicateIndexBy(final int times, final TemplateDescriptor templateDescriptor) {
    final Class<? extends OperateEntity> resultClass =
        descriptorToEntity.get(templateDescriptor.getClass());
    try {
      final List<? extends OperateEntity> results =
          findDocumentsFor(templateDescriptor, resultClass);
      if (results.isEmpty()) {
        LOGGER.info("No datasets for {} found.", templateDescriptor.getFullQualifiedName());
        return;
      }
      LOGGER.info(
          "Load {} {}. Duplicate it {} times.",
          results.size(),
          templateDescriptor.getFullQualifiedName(),
          times);
      duplicate(times, templateDescriptor, results);
      LOGGER.info(
          "Finished. Added {} documents of {}",
          results.size() * times,
          templateDescriptor.getFullQualifiedName());
    } catch (final Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  private List<? extends OperateEntity> findDocumentsFor(
      final TemplateDescriptor templateDescriptor, final Class<? extends OperateEntity> resultClass)
      throws IOException {
    final SearchResponse searchResponse =
        esClient.search(
            new SearchRequest(templateDescriptor.getIndexPattern())
                .source(SearchSourceBuilder.searchSource().size(MAX_DOCUMENTS)),
            RequestOptions.DEFAULT);
    return ElasticsearchUtil.mapSearchHits(
        searchResponse.getHits().getHits(), objectMapper, resultClass);
  }

  private void duplicate(
      final int times,
      final TemplateDescriptor templateDescriptor,
      final List<? extends OperateEntity> results)
      throws PersistenceException {
    final int max = times * results.size();
    int count = 0;
    for (int i = 0; i < times; i++) {
      final BulkRequest bulkRequest = new BulkRequest();
      results.forEach(
          item -> {
            item.setId(UUID.randomUUID().toString());
            try {
              bulkRequest.add(
                  new IndexRequest(templateDescriptor.getFullQualifiedName())
                      .id(item.getId())
                      .source(objectMapper.writeValueAsString(item), XContentType.JSON));
            } catch (final JsonProcessingException e) {
              LOGGER.error(e.getMessage());
            }
          });
      count += bulkRequest.requests().size();
      ElasticsearchUtil.processBulkRequest(
          esClient,
          bulkRequest,
          operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
      final int percentDone = Double.valueOf(100 * count / max).intValue();
      if (percentDone > 0 && percentDone % 20 == 0) {
        LOGGER.info(
            "{}/{} ( {}% ) documents added to {}.",
            count, max, percentDone, templateDescriptor.getFullQualifiedName());
      }
    }
  }
}
