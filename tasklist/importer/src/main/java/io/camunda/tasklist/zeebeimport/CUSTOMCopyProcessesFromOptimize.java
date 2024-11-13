/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.ConversionUtils.toStringOrNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.schema.v86.indices.FormIndex;
import io.camunda.tasklist.schema.v86.indices.ProcessIndex;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.zeebeimport.util.XMLUtil;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

// TODO This class was used on the recover of aliases (I'm keeping it here for one release just in
// case - This should be removed on 8.5 -- notice that is not enabled with @Component and is not
// being used anywhere)
public class CUSTOMCopyProcessesFromOptimize {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(CUSTOMCopyProcessesFromOptimize.class);
  private static final String OPTIMIZE_PROCESS_INDEX = "optimize-process-definition_v6";

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired private XMLUtil xmlUtil;

  @Autowired private ProcessIndex processIndex;

  @Autowired private FormIndex formIndex;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  public void copyProcesses() {
    String scrollId = null;
    try {
      final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().size(10);

      final SearchRequest searchRequest =
          new SearchRequest(OPTIMIZE_PROCESS_INDEX)
              .source(searchSourceBuilder)
              .requestCache(false)
              .scroll(TimeValue.timeValueMinutes(1));

      var searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      while (searchResponse.getHits().getHits().length > 0) {
        final BulkRequest bulkRequest = new BulkRequest();

        for (SearchHit hit : searchResponse.getHits().getHits()) {

          final Map<String, Object> processFromOptimize = hit.getSourceAsMap();

          if (!processFromOptimize.get("key").equals("customer_onboarding_en")) {
            final long processDefinitionKey = Long.valueOf((String) processFromOptimize.get("id"));

            if (!processDefinitionExists(processDefinitionKey, esClient)) {
              final Map<String, String> userTaskForms = new HashMap<>();
              final ProcessEntity processToTasklist =
                  createEntity(processFromOptimize, userTaskForms::put);
              try {
                bulkRequest.add(
                    new IndexRequest()
                        .index(processIndex.getFullQualifiedName())
                        .id(toStringOrNull(processToTasklist.getKey()))
                        .source(
                            objectMapper.writeValueAsString(processToTasklist), XContentType.JSON));

                userTaskForms.forEach(
                    (formKey, schema) -> {
                      try {
                        final FormEntity formEntity =
                            new FormEntity(
                                String.valueOf(processDefinitionKey),
                                formKey,
                                schema,
                                processToTasklist.getTenantId());
                        if (!formExists(formEntity.getId(), esClient)) {
                          persistForm(formEntity, bulkRequest);
                        }
                      } catch (Exception ex) {
                        LOGGER.warn("Unable to copy forms from Optimize: " + ex.getMessage(), ex);
                      }
                    });
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e.getMessage(), e);
              }
            }
          }
        }

        if (bulkRequest.requests().size() > 0) {
          esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        }

        scrollId = searchResponse.getScrollId();
        final SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
        scrollRequest.scroll(TimeValue.timeValueMinutes(1));

        searchResponse = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);

        LOGGER.info("Processes were successfully copied from Optimize.");
      }
    } catch (Exception ex) {
      LOGGER.warn("Unable to copy processes from Optimize: " + ex.getMessage(), ex);
    } finally {
      if (scrollId != null) {
        ElasticsearchUtil.clearScroll(scrollId, esClient);
      }
    }
  }

  private ProcessEntity createEntity(
      Map<String, Object> processFromOptimize, BiConsumer<String, String> userTaskFormCollector) {
    final ProcessEntity processEntity = new ProcessEntity();
    processEntity.setId(String.valueOf(processFromOptimize.get("id")));
    processEntity.setTenantId("<default>");
    processEntity.setKey(Long.valueOf(String.valueOf(processFromOptimize.get("id"))));
    processEntity.setBpmnProcessId(String.valueOf(processFromOptimize.get("key")));
    processEntity.setVersion(Integer.valueOf(String.valueOf(processFromOptimize.get("version"))));
    processEntity.setName(String.valueOf(processFromOptimize.get("name")));
    final String bpmXml = String.valueOf(processFromOptimize.get("bpmn20Xml"));

    xmlUtil.extractDiagramData(
        bpmXml.getBytes(),
        processEntity.getBpmnProcessId()::equals,
        processEntity::setName,
        flowNode -> processEntity.getFlowNodes().add(flowNode),
        userTaskFormCollector,
        processEntity::setFormKey,
        formId -> processEntity.setFormId(formId),
        processEntity::setStartedByForm);

    return processEntity;
  }

  private boolean processDefinitionExists(
      final long processDefinitionKey, final RestHighLevelClient elasticsearchClient)
      throws IOException {
    return elasticsearchClient.exists(
        new GetRequest(processIndex.getFullQualifiedName(), String.valueOf(processDefinitionKey)),
        RequestOptions.DEFAULT);
  }

  private boolean formExists(final String formId, final RestHighLevelClient elasticsearchClient)
      throws IOException {
    return elasticsearchClient.exists(
        new GetRequest(formIndex.getFullQualifiedName(), formId), RequestOptions.DEFAULT);
  }

  private void persistForm(FormEntity formEntity, BulkRequest bulkRequest)
      throws PersistenceException {

    LOGGER.debug("Form: key {}", formEntity.getId());
    try {
      bulkRequest.add(
          new IndexRequest()
              .index(formIndex.getFullQualifiedName())
              .id(ConversionUtils.toStringOrNull(formEntity.getId()))
              .source(objectMapper.writeValueAsString(formEntity), XContentType.JSON));

    } catch (JsonProcessingException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to insert task form [%s]", formEntity.getId()),
          e);
    }
  }
}
