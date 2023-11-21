/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.tasklist.util.ElasticsearchUtil.getRawResponseWithTenantCheck;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class FormStoreElasticSearch implements FormStore {

  @Autowired private FormIndex formIndex;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private ProcessIndex processIndex;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private RestHighLevelClient esClient;

  public FormEntity getForm(final String id, final String processDefinitionId, final Long version) {
    final FormEntity formEmbedded =
        version == null ? getFormEmbedded(id, processDefinitionId) : null;
    if (formEmbedded != null) {
      return formEmbedded;
    } else if (isFormAssociatedToTask(id, processDefinitionId)) {
      final var formLinked = getLinkedForm(id, version);
      if (formLinked != null) {
        return formLinked;
      }
    } else if (isFormAssociatedToProcess(id, processDefinitionId)) {
      final var formLinked = getLinkedForm(id, version);
      if (formLinked != null) {
        return formLinked;
      }
    }
    throw new NotFoundException(String.format("form with id %s was not found", id));
  }

  @Override
  public List<String> getFormIdsByProcessDefinitionId(String processDefinitionId) {
    final SearchRequest searchRequest =
        new SearchRequest(formIndex.getFullQualifiedName())
            .source(
                SearchSourceBuilder.searchSource()
                    .query(termQuery(FormIndex.PROCESS_DEFINITION_ID, processDefinitionId))
                    .fetchField(FormIndex.ID));
    try {
      return ElasticsearchUtil.scrollIdsToList(searchRequest, esClient);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  private FormEntity getFormEmbedded(final String id, final String processDefinitionId) {
    try {
      final String formId = String.format("%s_%s", processDefinitionId, id);
      final var formSearchHit =
          getRawResponseWithTenantCheck(formId, formIndex, ONLY_RUNTIME, tenantAwareClient);
      return fromSearchHit(formSearchHit.getSourceAsString(), objectMapper, FormEntity.class);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    } catch (NotFoundException e) {
      return null;
    }
  }

  private FormEntity getLinkedForm(final String formId, final Long formVersion) {
    final SearchRequest searchRequest = new SearchRequest(formIndex.getFullQualifiedName());
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
    boolQuery.must(QueryBuilders.termQuery(FormIndex.BPMN_ID, formId));
    if (formVersion != null) {
      // with the version set, you can return the form that was deleted, because of backward
      // compatibility
      boolQuery.must(QueryBuilders.termQuery(FormIndex.VERSION, formVersion));
    } else {
      // get the latest version where isDeleted is false (highest active version)
      boolQuery.must(QueryBuilders.termQuery(FormIndex.IS_DELETED, false));
      searchSourceBuilder.sort(FormIndex.VERSION, SortOrder.DESC);
      searchSourceBuilder.size(1);
    }

    searchSourceBuilder.query(boolQuery);
    searchRequest.source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      if (searchResponse.getHits().getHits().length > 0) {
        final Map<String, Object> sourceAsMap =
            searchResponse.getHits().getHits()[0].getSourceAsMap();
        final FormEntity formEntity = new FormEntity();
        formEntity.setBpmnId((String) sourceAsMap.get(FormIndex.BPMN_ID));
        formEntity.setVersion(((Number) sourceAsMap.get(FormIndex.VERSION)).longValue());
        formEntity.setEmbedded((Boolean) sourceAsMap.get(FormIndex.EMBEDDED));
        formEntity.setSchema((String) sourceAsMap.get(FormIndex.SCHEMA));
        formEntity.setTenantId((String) sourceAsMap.get(FormIndex.TENANT_ID));
        formEntity.setIsDeleted((Boolean) sourceAsMap.get(FormIndex.IS_DELETED));
        return formEntity;
      }
    } catch (IOException e) {
      final String formIdNotFoundMessage =
          String.format("Error retrieving the version for the formId: [%s]", formId);
      throw new TasklistRuntimeException(formIdNotFoundMessage);
    }
    return null;
  }

  private Boolean isFormAssociatedToTask(final String formId, final String processDefinitionId) {
    try {
      final BoolQueryBuilder boolQuery =
          QueryBuilders.boolQuery()
              .must(QueryBuilders.matchQuery(TaskTemplate.FORM_ID, formId))
              .must(
                  QueryBuilders.matchQuery(
                      TaskTemplate.PROCESS_DEFINITION_ID, processDefinitionId));

      final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
      searchSourceBuilder.query(boolQuery);

      final SearchRequest searchRequest = new SearchRequest(taskTemplate.getFullQualifiedName());
      searchRequest.source(searchSourceBuilder);

      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      return searchResponse.getHits().getTotalHits().value > 0;
    } catch (IOException e) {
      final String formIdNotFoundMessage =
          String.format("Error retrieving the version for the formId: [%s]", formId);
      throw new TasklistRuntimeException(formIdNotFoundMessage);
    }
  }

  private Boolean isFormAssociatedToProcess(final String formId, final String processDefinitionId) {
    try {
      final BoolQueryBuilder boolQuery =
          QueryBuilders.boolQuery()
              .must(QueryBuilders.matchQuery(ProcessIndex.FORM_ID, formId))
              .must(QueryBuilders.matchQuery(ProcessIndex.ID, processDefinitionId));

      final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
      searchSourceBuilder.query(boolQuery);

      final SearchRequest searchRequest = new SearchRequest(processIndex.getFullQualifiedName());
      searchRequest.source(searchSourceBuilder);

      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      return searchResponse.getHits().getTotalHits().value > 0;
    } catch (IOException e) {
      final String formIdNotFoundMessage =
          String.format("Error retrieving the version for the formId: [%s]", formId);
      throw new TasklistRuntimeException(formIdNotFoundMessage);
    }
  }
}
