/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.opensearch;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.ProcessStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.FieldCollapse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessStoreOpenSearch implements ProcessStore {

  private static final Boolean CASE_INSENSITIVE = true;

  @Autowired private ProcessIndex processIndex;

  @Autowired private OpenSearchClient openSearchClient;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private FormStore formStore;

  @Override
  public ProcessEntity getProcessByProcessDefinitionKey(String processDefinitionKey) {
    try {
      final FieldCollapse keyCollapse = new FieldCollapse.Builder().field(ProcessIndex.KEY).build();
      final SortOptions sortOptions =
          new SortOptions.Builder()
              .field(FieldSort.of(f -> f.field(ProcessIndex.VERSION).order(SortOrder.Desc)))
              .build();

      final SearchResponse<ProcessEntity> response =
          openSearchClient.search(
              s ->
                  s.index(List.of(processIndex.getAlias()))
                      .query(
                          q ->
                              q.term(
                                  t ->
                                      t.field(ProcessIndex.KEY)
                                          .value(FieldValue.of(processDefinitionKey))))
                      .collapse(keyCollapse)
                      .sort(sortOptions)
                      .size(1),
              ProcessEntity.class);
      if (response.hits().hits().size() > 0) {
        return response.hits().hits().get(0).source();
      } else {
        throw new NotFoundException(
            String.format("Process with key %s not found", processDefinitionKey));
      }
    } catch (IOException | OpenSearchException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ProcessEntity getProcessByBpmnProcessId(String bpmnProcessId) {
    final FieldCollapse keyCollapse =
        new FieldCollapse.Builder().field(ProcessIndex.PROCESS_DEFINITION_ID).build();
    final SortOptions sortOptions =
        new SortOptions.Builder()
            .field(
                FieldSort.of(
                    f -> f.field(ProcessIndex.PROCESS_DEFINITION_ID).order(SortOrder.Desc)))
            .build();

    final SearchResponse<ProcessEntity> response;
    try {
      response =
          openSearchClient.search(
              s ->
                  s.index(List.of(processIndex.getAlias()))
                      .query(
                          q ->
                              q.term(
                                  t ->
                                      t.field(ProcessIndex.KEY)
                                          .value(FieldValue.of(bpmnProcessId))))
                      .collapse(keyCollapse)
                      .sort(sortOptions)
                      .size(1),
              ProcessEntity.class);
      if (response.hits().hits().size() > 0) {
        return response.hits().hits().get(0).source();
      } else {
        throw new NotFoundException(String.format("Process with key %s not found", bpmnProcessId));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ProcessEntity getProcess(String processId) {

    final SearchResponse<ProcessEntity> response;
    try {
      response =
          openSearchClient.search(
              s ->
                  s.index(List.of(processIndex.getAlias()))
                      .query(
                          q ->
                              q.term(
                                  t -> t.field(ProcessIndex.KEY).value(FieldValue.of(processId)))),
              ProcessEntity.class);

      final long totalHits = response.hits().total().value();
      if (totalHits == 1L) {
        return response.hits().hits().get(0).source();
      } else if (totalHits > 1) {
        throw new TasklistRuntimeException(
            String.format("Could not find unique process with id '%s'.", processId));
      } else {
        throw new NotFoundException(
            String.format("Could not find process with id '%s'.", processId));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<ProcessEntity> getProcesses(final List<String> processDefinitions) {
    final FieldCollapse keyCollapse =
        new FieldCollapse.Builder().field(ProcessIndex.PROCESS_DEFINITION_ID).build();
    final SortOptions sortOptions =
        new SortOptions.Builder()
            .field(FieldSort.of(f -> f.field(ProcessIndex.VERSION).order(SortOrder.Desc)))
            .build();

    final Query q;

    if (tasklistProperties.isSelfManaged()) {

      if (processDefinitions.size() == 0) {
        return new ArrayList<ProcessEntity>();
      }

      if (processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        q =
            QueryBuilders.bool()
                .must(t -> t.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
                .mustNot(
                    mn ->
                        mn.term(
                            t ->
                                t.field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .value(FieldValue.of(""))))
                .build()
                ._toQuery();
      } else {
        q =
            QueryBuilders.bool()
                .must(
                    m ->
                        m.terms(
                            terms ->
                                terms
                                    .field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .terms(
                                        v ->
                                            v.value(
                                                processDefinitions.stream()
                                                    .map(pd -> FieldValue.of(pd))
                                                    .collect(Collectors.toList())))))
                .must(m -> m.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
                .mustNot(
                    mn ->
                        mn.term(
                            t ->
                                t.field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .value(FieldValue.of(""))))
                .build()
                ._toQuery();
      }
    } else {
      q =
          QueryBuilders.bool()
              .must(m -> m.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
              .mustNot(
                  mn ->
                      mn.term(
                          t ->
                              t.field(ProcessIndex.PROCESS_DEFINITION_ID).value(FieldValue.of(""))))
              .build()
              ._toQuery();
    }

    try {
      final SearchResponse<ProcessEntity> response =
          openSearchClient.search(
              s ->
                  s.index(List.of(processIndex.getAlias()))
                      .query(q)
                      .collapse(keyCollapse)
                      .sort(sortOptions),
              ProcessEntity.class);

      return response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());

    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<ProcessEntity> getProcesses(String search, final List<String> processDefinitions) {
    final FieldCollapse keyCollapse =
        new FieldCollapse.Builder().field(ProcessIndex.PROCESS_DEFINITION_ID).build();
    final SortOptions sortOptions =
        new SortOptions.Builder()
            .field(FieldSort.of(f -> f.field(ProcessIndex.VERSION).order(SortOrder.Desc)))
            .build();

    if (search == null || search.isBlank()) {
      return getProcesses(processDefinitions);
    }

    final Query query;
    final String regexSearch = String.format(".*%s.*", search);

    if (tasklistProperties.isSelfManaged()) {
      if (processDefinitions.size() == 0) {
        return new ArrayList<ProcessEntity>();
      }
      if (processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        query =
            QueryBuilders.bool()
                .should(s -> s.term(t -> t.field(ProcessIndex.ID).value(FieldValue.of(search))))
                .should(s -> s.regexp(regex -> regex.field(ProcessIndex.NAME).value(regexSearch)))
                .should(
                    s ->
                        s.regexp(
                            regex ->
                                regex.field(ProcessIndex.PROCESS_DEFINITION_ID).value(regexSearch)))
                .must(m -> m.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
                .mustNot(
                    mn ->
                        mn.term(
                            t ->
                                t.field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .value(FieldValue.of(""))))
                .minimumShouldMatch("1")
                .build()
                ._toQuery();
      } else {
        query =
            QueryBuilders.bool()
                .should(s -> s.term(t -> t.field(ProcessIndex.ID).value(FieldValue.of(search))))
                .should(
                    s ->
                        s.regexp(
                            regex ->
                                regex
                                    .field(ProcessIndex.NAME)
                                    .value(regexSearch)
                                    .caseInsensitive(CASE_INSENSITIVE)))
                .should(
                    s ->
                        s.regexp(
                            regex ->
                                regex
                                    .field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .value(regexSearch)
                                    .caseInsensitive(CASE_INSENSITIVE)))
                .must(m -> m.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
                .mustNot(
                    mn ->
                        mn.term(
                            t ->
                                t.field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .value(FieldValue.of(""))))
                .build()
                ._toQuery();
      }
    } else {
      query =
          QueryBuilders.bool()
              .should(s -> s.term(t -> t.field(ProcessIndex.ID).value(FieldValue.of(search))))
              .should(
                  s ->
                      s.regexp(
                          regex ->
                              regex
                                  .field(ProcessIndex.NAME)
                                  .value(regexSearch)
                                  .caseInsensitive(CASE_INSENSITIVE)))
              .should(
                  s ->
                      s.regexp(
                          regex ->
                              regex
                                  .field(ProcessIndex.PROCESS_DEFINITION_ID)
                                  .value(regexSearch)
                                  .caseInsensitive(CASE_INSENSITIVE)))
              .must(m -> m.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
              .mustNot(
                  mn ->
                      mn.term(
                          t ->
                              t.field(ProcessIndex.PROCESS_DEFINITION_ID).value(FieldValue.of(""))))
              .minimumShouldMatch("1")
              .build()
              ._toQuery();
    }

    try {
      final SearchResponse<ProcessEntity> response =
          openSearchClient.search(
              s ->
                  s.index(List.of(processIndex.getAlias()))
                      .query(query)
                      .collapse(keyCollapse)
                      .sort(sortOptions),
              ProcessEntity.class);

      return response.hits().hits().stream().map(h -> h.source()).collect(Collectors.toList());

    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<ProcessEntity> getProcessesStartedByForm() {
    final FieldCollapse keyCollapse =
        new FieldCollapse.Builder().field(ProcessIndex.PROCESS_DEFINITION_ID).build();
    final SortOptions sortOptions =
        new SortOptions.Builder()
            .field(FieldSort.of(f -> f.field(ProcessIndex.VERSION).order(SortOrder.Desc)))
            .build();

    final SearchResponse<ProcessEntity> response;

    try {
      response =
          openSearchClient.search(
              s ->
                  s.index(processIndex.getAlias())
                      .query(
                          q ->
                              q.bool(
                                  b ->
                                      b.must(
                                              m ->
                                                  m.exists(
                                                      e ->
                                                          e.field(
                                                              ProcessIndex.PROCESS_DEFINITION_ID)))
                                          .mustNot(
                                              mn ->
                                                  mn.term(
                                                      t ->
                                                          t.field(
                                                                  ProcessIndex
                                                                      .PROCESS_DEFINITION_ID)
                                                              .value(FieldValue.of(""))))))
                      .collapse(keyCollapse)
                      .sort(sortOptions),
              ProcessEntity.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return response.hits().hits().stream().map(h -> h.source()).collect(Collectors.toList());
  }
}
