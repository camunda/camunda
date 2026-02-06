/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.ScrollException;
import io.camunda.tasklist.util.ElasticsearchTenantHelper;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.util.ElasticsearchUtil.QueryType;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.form.FormEntity;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

  @Autowired private ProcessIndex processIndex;

  @Autowired private ElasticsearchTenantHelper tenantHelper;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired
  @Qualifier("tasklistEsClient")
  private ElasticsearchClient esClient;

  @Override
  public FormEntity getForm(final String id, final String processDefinitionId, final Long version) {
    // Try to get embedded form first (only when version is not specified)
    if (version == null) {
      final FormEntity formEmbedded = getFormEmbedded(id, processDefinitionId);
      if (formEmbedded != null) {
        return formEmbedded;
      }
    }

    // Try to get linked form if associated to task or process
    final Optional<String> tenantId =
        getTenantIfFormAssociatedToTask(id, processDefinitionId)
            .or(() -> getTenantIfFormAssociatedToProcess(id, processDefinitionId));
    if (tenantId.isPresent()) {
      final var formLinked = getLinkedForm(id, version, tenantId.get());
      if (formLinked != null) {
        return formLinked;
      }
    }

    throw new NotFoundException(String.format("form with id %s was not found", id));
  }

  @Override
  public List<String> getFormIdsByProcessDefinitionId(final String processDefinitionId) {
    final var query =
        ElasticsearchUtil.termsQuery(FormIndex.PROCESS_DEFINITION_ID, processDefinitionId);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(formIndex.getFullQualifiedName())
            .query(query)
            .source(s -> s.filter(f -> f.includes(FormIndex.ID)));

    try {
      return ElasticsearchUtil.scrollAllStream(
              esClient, searchRequestBuilder, ElasticsearchUtil.MAP_CLASS)
          .flatMap(response -> response.hits().hits().stream())
          .map(Hit::id)
          .filter(Objects::nonNull)
          .toList();
    } catch (final ScrollException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public Optional<FormIdView> getFormByKey(final String formKey) {
    final var getRequest =
        GetRequest.of(b -> b.index(formIndex.getFullQualifiedName()).id(formKey));

    try {
      final var response = esClient.get(getRequest, ElasticsearchUtil.MAP_CLASS);
      if (response.found()) {
        final var sourceAsMap = response.source();
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
    final String formId = String.format("%s_%s", processDefinitionId, id);
    final var idsQuery = ElasticsearchUtil.idsQuery(formId);
    final var query = ElasticsearchUtil.constantScoreQuery(idsQuery);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequest =
        new SearchRequest.Builder()
            .index(
                ElasticsearchUtil.whereToSearch(
                    formIndex, ElasticsearchUtil.QueryType.ONLY_RUNTIME))
            .query(tenantAwareQuery)
            .build();

    try {
      final var response = esClient.search(searchRequest, FormEntity.class);
      if (response.hits().total().value() == 1) {
        return response.hits().hits().get(0).source();
      }
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
    return null;
  }

  private FormEntity getLinkedForm(
      final String formId, final Long formVersion, final String tenantId) {
    try {
      final var bpmnIdQuery = ElasticsearchUtil.termsQuery(FormIndex.BPMN_ID, formId);
      final var idQuery = ElasticsearchUtil.termsQuery(FormIndex.ID, formId);
      final var formIdQuery =
          Query.of(q -> q.bool(b -> b.should(bpmnIdQuery).should(idQuery).minimumShouldMatch("1")));

      // with the version set, you can return the form that was deleted, because of backward
      // compatibility
      final var query =
          formVersion != null
              ? ElasticsearchUtil.joinWithAnd(
                  formIdQuery, ElasticsearchUtil.termsQuery(FormIndex.VERSION, formVersion))
              : ElasticsearchUtil.joinWithAnd(
                  formIdQuery, ElasticsearchUtil.termsQuery(FormIndex.IS_DELETED, false));

      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query, List.of(tenantId));

      final var searchRequestBuilder =
          new SearchRequest.Builder()
              .index(formIndex.getFullQualifiedName())
              .query(tenantAwareQuery)
              .size(1);

      // get the latest version where IS_DELETED is false (highest active version)
      if (formVersion == null) {
        searchRequestBuilder.sort(ElasticsearchUtil.sortOrder(FormIndex.VERSION, SortOrder.Desc));
      }

      final var response =
          esClient.search(searchRequestBuilder.build(), ElasticsearchUtil.MAP_CLASS);

      if (!response.hits().hits().isEmpty()) {
        final var sourceAsMap = response.hits().hits().get(0).source();
        final var formEntity = new FormEntity();
        formEntity.setFormId((String) sourceAsMap.get(FormIndex.BPMN_ID));
        formEntity.setVersion(((Number) sourceAsMap.get(FormIndex.VERSION)).longValue());
        formEntity.setEmbedded((Boolean) sourceAsMap.get(FormIndex.EMBEDDED));
        formEntity.setSchema((String) sourceAsMap.get(FormIndex.SCHEMA));
        formEntity.setTenantId((String) sourceAsMap.get(FormIndex.TENANT_ID));
        formEntity.setIsDeleted((Boolean) sourceAsMap.get(FormIndex.IS_DELETED));
        return formEntity;
      }
    } catch (final IOException e) {
      final var formIdNotFoundMessage =
          String.format("Error retrieving the version for the formId: [%s]", formId);
      throw new TasklistRuntimeException(formIdNotFoundMessage);
    }
    return null;
  }

  private Optional<String> getTenantIfFormAssociatedToTask(
      final String formId, final String processDefinitionId) {
    try {
      final var formIdMatchQuery =
          Query.of(q -> q.match(m -> m.field(TaskTemplate.FORM_ID).query(formId)));
      final var formKeyMatchQuery =
          Query.of(q -> q.match(m -> m.field(TaskTemplate.FORM_KEY).query(formId)));
      final var formQuery =
          Query.of(
              q ->
                  q.bool(
                      b ->
                          b.should(formIdMatchQuery)
                              .should(formKeyMatchQuery)
                              .minimumShouldMatch("1")));

      final var processDefQuery =
          Query.of(
              q ->
                  q.match(
                      m -> m.field(TaskTemplate.PROCESS_DEFINITION_ID).query(processDefinitionId)));

      final var combinedQuery = ElasticsearchUtil.joinWithAnd(formQuery, processDefQuery);
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(combinedQuery);

      final var searchRequest =
          new SearchRequest.Builder()
              .index(taskTemplate.getFullQualifiedName())
              .query(tenantAwareQuery)
              .build();

      final var response = esClient.search(searchRequest, ElasticsearchUtil.MAP_CLASS);
      if (response.hits().total().value() > 0) {
        return Optional.of(
            (String) response.hits().hits().get(0).source().get(TaskTemplate.TENANT_ID));
      } else {
        return Optional.empty();
      }
    } catch (final IOException e) {
      final var formIdNotFoundMessage =
          String.format("Error retrieving the version for the formId: [%s]", formId);
      throw new TasklistRuntimeException(formIdNotFoundMessage);
    }
  }

  private Optional<String> getTenantIfFormAssociatedToProcess(
      final String formId, final String processDefinitionId) {
    try {
      final var formIdQuery =
          Query.of(q -> q.match(m -> m.field(ProcessIndex.FORM_ID).query(formId)));
      final var processDefQuery =
          Query.of(q -> q.match(m -> m.field(ProcessIndex.ID).query(processDefinitionId)));

      final var combinedQuery = ElasticsearchUtil.joinWithAnd(formIdQuery, processDefQuery);
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(combinedQuery);

      final var searchRequest =
          new SearchRequest.Builder()
              .index(ElasticsearchUtil.whereToSearch(processIndex, QueryType.ONLY_RUNTIME))
              .query(tenantAwareQuery)
              .build();

      final var response = esClient.search(searchRequest, ElasticsearchUtil.MAP_CLASS);
      if (response.hits().total().value() > 0) {
        return Optional.of(
            (String) response.hits().hits().get(0).source().get(TaskTemplate.TENANT_ID));
      } else {
        return Optional.empty();
      }
    } catch (final IOException e) {
      final var formIdNotFoundMessage =
          String.format("Error retrieving the version for the formId: [%s]", formId);
      throw new TasklistRuntimeException(formIdNotFoundMessage);
    }
  }
}
