/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
          operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes(),
          operateProperties.getElasticsearch().isBulkRequestIgnoreNullIndex());
      final int percentDone = Double.valueOf(100 * count / max).intValue();
      if (percentDone > 0 && percentDone % 20 == 0) {
        LOGGER.info(
            "{}/{} ( {}% ) documents added to {}.",
            count, max, percentDone, templateDescriptor.getFullQualifiedName());
      }
    }
  }
}
