/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.migration.performance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.entities.*;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.*;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.camunda.operate.util.CollectionUtil.filter;
import static io.camunda.operate.util.CollectionUtil.map;

@SpringBootApplication
@ComponentScan(basePackages = {
    "io.camunda.operate.property", "io.camunda.operate.es",
    "io.camunda.operate.schema.templates","io.camunda.operate.schema.indices"
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
@Import(JacksonConfig.class)
public class DataMultiplicator implements CommandLineRunner {

  public static final int MAX_DOCUMENTS = 10_000;
  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private List<TemplateDescriptor> indexDescriptors;

  private Map<Class<? extends TemplateDescriptor>, Class<? extends OperateEntity>> descriptorToEntity = Map.of(
      EventTemplate.class, EventEntity.class,
      SequenceFlowTemplate.class, SequenceFlowEntity.class,
      VariableTemplate.class, VariableEntity.class,
      IncidentTemplate.class, IncidentEntity.class
  );

  @Autowired
  private ObjectMapper objectMapper;

  private static final Logger logger = LoggerFactory.getLogger(DataMultiplicator.class);

  @Override public void run(String... args) throws Exception {
    final int[] times = {500};
    try {
      times[0] = Integer.parseInt(args[0]);
    } catch (Exception e){
      logger.warn("Couldn't parse times of duplication. Use default {}", times[0]);
    }
    List<TemplateDescriptor> duplicatable = filter(indexDescriptors, descriptor -> descriptorToEntity.containsKey(descriptor.getClass()));
    List<Thread> duplicators = map(duplicatable, descriptor -> new Thread(() -> duplicateIndexBy(times[0], descriptor)));
    duplicators.forEach(Thread::start);
    for (Thread t: duplicators) {
      t.join();
    }
  }

  private void duplicateIndexBy(int times, TemplateDescriptor templateDescriptor){
    Class<? extends OperateEntity> resultClass = descriptorToEntity.get(templateDescriptor.getClass());
    try {
      List<? extends OperateEntity> results = findDocumentsFor(templateDescriptor, resultClass);
      if(results.isEmpty()) {
        logger.info("No datasets for {} found.", templateDescriptor.getFullQualifiedName());
        return;
      }
      logger.info("Load {} {}. Duplicate it {} times.", results.size(), templateDescriptor.getFullQualifiedName(), times);
      duplicate(times, templateDescriptor, results);
      logger.info("Finished. Added {} documents of {}",results.size() * times, templateDescriptor.getFullQualifiedName());
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  private List<? extends OperateEntity> findDocumentsFor(TemplateDescriptor templateDescriptor, Class<? extends OperateEntity> resultClass)
      throws IOException {
    SearchResponse searchResponse = esClient.search(new SearchRequest(templateDescriptor.getIndexPattern())
        .source(SearchSourceBuilder.searchSource().size(MAX_DOCUMENTS)), RequestOptions.DEFAULT);
    return ElasticsearchUtil.mapSearchHits(searchResponse.getHits().getHits(),objectMapper, resultClass);
  }

  private void duplicate(int times, TemplateDescriptor templateDescriptor, List<? extends OperateEntity> results) throws PersistenceException {
    int max = times * results.size();
    int count = 0;
    for(int i = 0; i < times; i++) {
      BulkRequest bulkRequest = new BulkRequest();
      results.forEach(item -> {
        item.setId(UUID.randomUUID().toString());
        try {
          bulkRequest.add(new IndexRequest(templateDescriptor.getFullQualifiedName()).id(item.getId()).
              source(objectMapper.writeValueAsString(item), XContentType.JSON));
        } catch (JsonProcessingException e) {
          logger.error(e.getMessage());
        }
      });
      count += bulkRequest.requests().size();
      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
      int percentDone = Double.valueOf(100 * count/max).intValue();
      if(percentDone > 0 && percentDone % 20 == 0){
        logger.info("{}/{} ( {}% ) documents added to {}.", count, max, percentDone, templateDescriptor.getFullQualifiedName());
      }
    }
  }

  public static void main(String[] args) {
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    final SpringApplication springApplication = new SpringApplication(DataMultiplicator.class);
    springApplication.setWebApplicationType(WebApplicationType.NONE);
    springApplication.setAddCommandLineProperties(true);
    final ConfigurableApplicationContext ctx = springApplication.run(args);
    SpringApplication.exit(ctx);
  }
}
