/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.util.OpenSearchUtil.getRawResponseWithTenantCheck;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.util.OpenSearchUtil.QueryType;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.form.FormEntity;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class FormStoreOpenSearch implements FormStore {

  private static final Logger LOG = LoggerFactory.getLogger(FormStoreOpenSearch.class);

  @Autowired private FormIndex formIndex;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired
  @Qualifier("tasklistProcessIndex")
  private ProcessIndex processIndex;

  @Autowired private TenantAwareOpenSearchClient tenantAwareClient;

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient osClient;

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
    final SearchRequest.Builder searchRequest =
        OpenSearchUtil.createSearchRequest(formIndex, QueryType.ONLY_RUNTIME)
            .query(
                q ->
                    q.term(
                        term ->
                            term.field(FormIndex.PROCESS_DEFINITION_ID)
                                .value(FieldValue.of(processDefinitionId))))
            .fields(f -> f.field(FormIndex.ID));
    try {
      return OpenSearchUtil.scrollIdsToList(searchRequest, osClient);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public Optional<FormIdView> getFormByKey(final String formKey) {
    final GetRequest request =
        new GetRequest.Builder().index(formIndex.getFullQualifiedName()).id(formKey).build();
    try {
      final GetResponse<FormIdView> response = osClient.get(request, FormIdView.class);
      if (response.found() && response.source() != null) {
        return Optional.of(response.source());
      }
    } catch (final OpenSearchException e) {
      if (e.response().status() == HttpURLConnection.HTTP_NOT_FOUND) {
        LOG.debug("Form with key {} not found.", formKey, e);
        return Optional.empty();
      }
      throw new TasklistRuntimeException(e);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
    return Optional.empty();
  }

  private Boolean isFormAssociatedToTask(final String formId, final String processDefinitionId) {
    try {
      final SearchRequest.Builder searchRequest =
          OpenSearchUtil.createSearchRequest(taskTemplate)
              .size(1) // only need to know if at least one exists
              .query(
                  b ->
                      b.bool(
                          bool ->
                              bool.must(
                                      must ->
                                          must.bool(
                                              boolIds ->
                                                  boolIds
                                                      .should(
                                                          q1 ->
                                                              q1.match(
                                                                  m ->
                                                                      m.field(TaskTemplate.FORM_ID)
                                                                          .query(
                                                                              FieldValue.of(
                                                                                  formId))))
                                                      .should(
                                                          q2 ->
                                                              q2.match(
                                                                  m ->
                                                                      m.field(TaskTemplate.FORM_KEY)
                                                                          .query(
                                                                              FieldValue.of(
                                                                                  formId))))
                                                      .minimumShouldMatch("1")))
                                  .must(
                                      q ->
                                          q.match(
                                              m ->
                                                  m.field(TaskTemplate.PROCESS_DEFINITION_ID)
                                                      .query(
                                                          FieldValue.of(processDefinitionId))))));

      final var searchResponse = tenantAwareClient.search(searchRequest, TaskTemplate.class);

      return searchResponse.hits().total().value() > 0;
    } catch (final IOException e) {
      final String formIdNotFoundMessage =
          String.format(
              "Error retrieving the association for the formId: [%s] and processDefinitionId: [%s]",
              formId, processDefinitionId);
      throw new TasklistRuntimeException(formIdNotFoundMessage);
    }
  }

  private Boolean isFormAssociatedToProcess(final String formId, final String processDefinitionId) {
    try {
      final SearchRequest.Builder searchRequest =
          OpenSearchUtil.createSearchRequest(processIndex, QueryType.ONLY_RUNTIME)
              .size(1) // only need to know if at least one exists
              .query(
                  b ->
                      b.bool(
                          bool ->
                              bool.must(
                                      q ->
                                          q.match(
                                              m ->
                                                  m.field(ProcessIndex.FORM_ID)
                                                      .query(FieldValue.of(formId))))
                                  .must(
                                      q ->
                                          q.match(
                                              m ->
                                                  m.field(ProcessIndex.ID)
                                                      .query(
                                                          FieldValue.of(processDefinitionId))))));

      final var searchResponse = tenantAwareClient.search(searchRequest, ProcessIndex.class);

      return searchResponse.hits().total().value() > 0;
    } catch (final IOException e) {
      final String formIdNotFoundMessage =
          String.format(
              "Error retrieving the association for the formId: [%s] and processDefinitionId: [%s]",
              formId, processDefinitionId);
      throw new TasklistRuntimeException(formIdNotFoundMessage);
    }
  }

  public FormEntity getLinkedForm(final String formId, final Long formVersion) {
    try {
      final Query boolQuery;
      final SearchRequest.Builder searchRequest =
          OpenSearchUtil.createSearchRequest(formIndex, QueryType.ALL)
              .index(formIndex.getFullQualifiedName())
              .size(1);

      final Query.Builder bpmnIdProcessQ = new Query.Builder();

      bpmnIdProcessQ.bool(
          boolQ ->
              boolQ
                  .should(
                      q1 ->
                          q1.terms(
                              terms ->
                                  terms
                                      .field(FormIndex.BPMN_ID)
                                      .terms(
                                          t ->
                                              t.value(
                                                  Collections.singletonList(
                                                      FieldValue.of(formId))))))
                  .should(
                      q2 ->
                          q2.terms(
                              terms ->
                                  terms
                                      .field(FormIndex.ID)
                                      .terms(
                                          t ->
                                              t.value(
                                                  Collections.singletonList(
                                                      FieldValue.of(formId))))))
                  .minimumShouldMatch("1"));

      if (formVersion == null) {
        // get the latest version where isDeleted is false (highest active version)
        final Query.Builder isDeleteQ = new Query.Builder();
        isDeleteQ.terms(
            terms ->
                terms
                    .field(FormIndex.IS_DELETED)
                    .terms(t -> t.value(Collections.singletonList(FieldValue.of(false)))));
        boolQuery = OpenSearchUtil.joinWithAnd(bpmnIdProcessQ, isDeleteQ);
        searchRequest.sort(s -> s.field(f -> f.field(FormIndex.VERSION).order(SortOrder.Desc)));
      } else {
        // with the version set, you can return the form that was deleted, because of backward
        // compatibility
        final Query.Builder isVersionQ = new Query.Builder();
        isVersionQ.terms(
            terms ->
                terms
                    .field(FormIndex.VERSION)
                    .terms(t -> t.value(Collections.singletonList(FieldValue.of(formVersion)))));
        boolQuery = OpenSearchUtil.joinWithAnd(bpmnIdProcessQ, isVersionQ);
      }
      searchRequest.query(boolQuery);

      final var formEntityResponse = tenantAwareClient.search(searchRequest, FormEntity.class);

      if (!formEntityResponse.hits().hits().isEmpty()) {
        return formEntityResponse.hits().hits().get(0).source();
      } else {
        return null;
      }
    } catch (final IOException e) {
      final String formIdNotFoundMessage =
          String.format("Error retrieving the version for the formId: [%s]", formId);
      throw new TasklistRuntimeException(formIdNotFoundMessage);
    }
  }

  public FormEntity getFormEmbedded(final String id, final String processDefinitionId) {
    try {
      final String formId = String.format("%s_%s", processDefinitionId, id);
      return getRawResponseWithTenantCheck(
          formId, formIndex, QueryType.ONLY_RUNTIME, tenantAwareClient, FormEntity.class);
    } catch (final IOException | OpenSearchException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    } catch (final NotFoundException e) {
      return null;
    }
  }
}
