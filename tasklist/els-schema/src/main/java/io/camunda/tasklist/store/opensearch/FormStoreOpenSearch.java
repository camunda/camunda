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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class FormStoreOpenSearch implements FormStore {

  @Autowired private FormIndex formIndex;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private ProcessIndex processIndex;

  @Autowired private TenantAwareOpenSearchClient tenantAwareClient;

  @Autowired
  @Qualifier("openSearchClient")
  private OpenSearchClient osClient;

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
      final Query boolQuery;
      final SearchRequest.Builder searchRequest =
          OpenSearchUtil.createSearchRequest(formIndex, OpenSearchUtil.QueryType.ALL)
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

  @Override
  public List<String> getFormIdsByProcessDefinitionId(String processDefinitionId) {
    final SearchRequest.Builder searchRequest =
        OpenSearchUtil.createSearchRequest(formIndex, ONLY_RUNTIME)
            .query(
                q ->
                    q.term(
                        term ->
                            term.field(FormIndex.PROCESS_DEFINITION_ID)
                                .value(FieldValue.of(processDefinitionId))))
            .fields(f -> f.field(TaskTemplate.ID));
    try {
      return OpenSearchUtil.scrollIdsToList(searchRequest, osClient);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public Optional<FormIdView> getHighestVersionFormByKey(String formKey) {
    try {
      final var formEntityResponse =
          osClient.search(
              b ->
                  b.index(formIndex.getFullQualifiedName())
                      .query(q -> q.term(t -> t.field(FormIndex.ID).value(FieldValue.of(formKey))))
                      .sort(s -> s.field(f -> f.field(FormIndex.VERSION).order(SortOrder.Desc)))
                      .source(
                          s ->
                              s.filter(
                                  f ->
                                      f.includes(
                                          List.of(
                                              FormIndex.ID, FormIndex.BPMN_ID, FormIndex.VERSION))))
                      .size(1),
              FormEntity.class);
      if (formEntityResponse.hits().total().value() == 1L) {
        final FormEntity formEntity = formEntityResponse.hits().hits().get(0).source();
        return Optional.of(
            new FormIdView(formEntity.getId(), formEntity.getBpmnId(), formEntity.getVersion()));
      } else {
        return Optional.empty();
      }
    } catch (IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }
}
