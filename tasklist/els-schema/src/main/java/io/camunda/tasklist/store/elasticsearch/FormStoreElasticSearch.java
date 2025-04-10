/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.tasklist.util.ElasticsearchUtil.getRawResponseWithTenantCheck;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.util.ElasticsearchUtil.QueryType;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.form.FormEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class FormStoreElasticSearch implements FormStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(FormStoreElasticSearch.class);

  @Autowired private FormIndex formIndex;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired
  @Qualifier("tasklistProcessIndex")
  private ProcessIndex processIndex;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Override
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
  public List<String> getFormIdsByProcessDefinitionId(final String processDefinitionId) {
    final SearchRequest searchRequest =
        new SearchRequest(formIndex.getFullQualifiedName())
            .source(
                SearchSourceBuilder.searchSource()
                    .query(termQuery(FormIndex.PROCESS_DEFINITION_ID, processDefinitionId))
                    .fetchField(FormIndex.ID));
    try {
      return ElasticsearchUtil.scrollIdsToList(searchRequest, esClient);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public Optional<FormIdView> getFormByKey(final String formKey) {
    final GetRequest getRequest = new GetRequest(formIndex.getFullQualifiedName(), formKey);

    try {
      final GetResponse response = esClient.get(getRequest, RequestOptions.DEFAULT);
      if (response.isExists()) {
        final Map<String, Object> sourceAsMap = response.getSourceAsMap();
        return Optional.of(
            new FormIdView(
                (String) sourceAsMap.get(FormIndex.ID),
                (String) sourceAsMap.get(FormIndex.BPMN_ID),
                ((Number) sourceAsMap.get(FormIndex.VERSION)).longValue()));
      }
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format("Error retrieving the last version for the formKey: %s", formKey), e);
    }
    return Optional.empty();
  }

  private FormEntity getFormEmbedded(final String id, final String processDefinitionId) {
    try {
      final String formId = String.format("%s_%s", processDefinitionId, id);
      final var formSearchHit =
          getRawResponseWithTenantCheck(
              formId, formIndex, QueryType.ONLY_RUNTIME, tenantAwareClient);
      return fromSearchHit(formSearchHit.getSourceAsString(), objectMapper, FormEntity.class);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    } catch (final NotFoundException e) {
      return null;
    }
  }

  private FormEntity getLinkedForm(final String formId, final Long formVersion) {
    final SearchRequest searchRequest = new SearchRequest(formIndex.getFullQualifiedName());
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
    boolQuery.must(
        QueryBuilders.boolQuery()
            .should(QueryBuilders.termQuery(FormIndex.BPMN_ID, formId))
            .should(QueryBuilders.termQuery(FormIndex.ID, formId))
            .minimumShouldMatch(1));
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
        formEntity.setFormId((String) sourceAsMap.get(FormIndex.BPMN_ID));
        formEntity.setVersion(((Number) sourceAsMap.get(FormIndex.VERSION)).longValue());
        formEntity.setEmbedded((Boolean) sourceAsMap.get(FormIndex.EMBEDDED));
        formEntity.setSchema((String) sourceAsMap.get(FormIndex.SCHEMA));
        formEntity.setTenantId((String) sourceAsMap.get(FormIndex.TENANT_ID));
        formEntity.setIsDeleted((Boolean) sourceAsMap.get(FormIndex.IS_DELETED));
        return formEntity;
      }
    } catch (final IOException e) {
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
              .must(
                  QueryBuilders.boolQuery()
                      .should(QueryBuilders.matchQuery(TaskTemplate.FORM_ID, formId))
                      .should(QueryBuilders.matchQuery(TaskTemplate.FORM_KEY, formId))
                      .minimumShouldMatch(1))
              .must(
                  QueryBuilders.matchQuery(
                      TaskTemplate.PROCESS_DEFINITION_ID, processDefinitionId));

      final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
      searchSourceBuilder.query(boolQuery);

      final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(taskTemplate);
      searchRequest.source(searchSourceBuilder);

      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      return searchResponse.getHits().getTotalHits().value > 0;
    } catch (final IOException e) {
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

      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(processIndex, QueryType.ONLY_RUNTIME);
      searchRequest.source(searchSourceBuilder);

      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      return searchResponse.getHits().getTotalHits().value > 0;
    } catch (final IOException e) {
      final String formIdNotFoundMessage =
          String.format("Error retrieving the version for the formId: [%s]", formId);
      throw new TasklistRuntimeException(formIdNotFoundMessage);
    }
  }
}
