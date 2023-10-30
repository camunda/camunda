/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.util.OpenSearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.tasklist.util.OpenSearchUtil.getRawResponseWithTenantCheck;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.util.OpenSearchUtil;
import java.io.IOException;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class FormStoreOpenSearch implements FormStore {

  @Autowired private FormIndex formIndex;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private ProcessIndex processIndex;

  @Autowired private TenantAwareOpenSearchClient tenantAwareClient;

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

  private Boolean isFormAssociatedToTask(final String formId, final String processDefinitionId) {
    try {
      final SearchRequest.Builder searchRequest =
          OpenSearchUtil.createSearchRequest(taskTemplate, OpenSearchUtil.QueryType.ALL)
              .index(taskTemplate.getFullQualifiedName())
              .size(1) // only need to know if at least one exists
              .query(
                  b ->
                      b.bool(
                          bool ->
                              bool.must(
                                      q ->
                                          q.match(
                                              m ->
                                                  m.field(TaskTemplate.FORM_ID)
                                                      .query(FieldValue.of(formId))))
                                  .must(
                                      q ->
                                          q.match(
                                              m ->
                                                  m.field(TaskTemplate.PROCESS_DEFINITION_ID)
                                                      .query(
                                                          FieldValue.of(processDefinitionId))))));

      final var searchResponse = tenantAwareClient.search(searchRequest, TaskTemplate.class);

      return searchResponse.hits().total().value() > 0;
    } catch (IOException e) {
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
          OpenSearchUtil.createSearchRequest(processIndex, OpenSearchUtil.QueryType.ALL)
              .index(processIndex.getFullQualifiedName())
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
    } catch (IOException e) {
      final String formIdNotFoundMessage =
          String.format(
              "Error retrieving the association for the formId: [%s] and processDefinitionId: [%s]",
              formId, processDefinitionId);
      throw new TasklistRuntimeException(formIdNotFoundMessage);
    }
  }

  public FormEntity getLinkedForm(final String formId, final Long formVersion) {
    try {
      final SearchRequest.Builder searchRequest =
          OpenSearchUtil.createSearchRequest(formIndex, OpenSearchUtil.QueryType.ALL)
              .index(formIndex.getFullQualifiedName())
              .query(q -> q.term(t -> t.field(FormIndex.BPMN_ID).value(FieldValue.of(formId))))
              .size(1);

      if (formVersion == null) {
        searchRequest.sort(s -> s.field(f -> f.field(FormIndex.VERSION).order(SortOrder.Desc)));
      } else {
        searchRequest.query(
            q -> q.term(t -> t.field(FormIndex.VERSION).value(FieldValue.of(formVersion))));
      }

      final var formEntityResponse = tenantAwareClient.search(searchRequest, FormEntity.class);

      if (!formEntityResponse.hits().hits().isEmpty()) {
        return formEntityResponse.hits().hits().get(0).source();
      } else {
        return null;
      }
    } catch (IOException e) {
      final String formIdNotFoundMessage =
          String.format("Error retrieving the version for the formId: [%s]", formId);
      throw new TasklistRuntimeException(formIdNotFoundMessage);
    }
  }

  public FormEntity getFormEmbedded(final String id, final String processDefinitionId) {
    try {
      final String formId = String.format("%s_%s", processDefinitionId, id);
      return getRawResponseWithTenantCheck(
          formId, formIndex, ONLY_RUNTIME, tenantAwareClient, FormEntity.class);
    } catch (IOException | OpenSearchException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    } catch (NotFoundException e) {
      return null;
    }
  }
}
