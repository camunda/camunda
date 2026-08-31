/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.FLOW_NODE_DATA;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.USER_TASK_NAMES;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.db.writer.DeletedProcessDefinitionFilter;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDefinitionWriterES extends AbstractProcessDefinitionWriterES
    implements ProcessDefinitionWriter {

  private static final Script MARK_AS_DELETED_SCRIPT =
      Script.of(
          s ->
              s.lang(ScriptLanguage.Painless)
                  .source(
                      "ctx._source.deleted = true;"
                          + " ctx._source."
                          + PROCESS_DEFINITION_XML
                          + " = null;"
                          + " ctx._source."
                          + FLOW_NODE_DATA
                          + " = null;"
                          + " ctx._source."
                          + USER_TASK_NAMES
                          + " = null"));

  private static final Script MARK_AS_ONBOARDED_SCRIPT =
      Script.of(s -> s.lang(ScriptLanguage.Painless).source("ctx._source.onboarded = true"));
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessDefinitionWriterES.class);

  private final ConfigurationService configurationService;
  private final DeletedProcessDefinitionFilter deletedProcessDefinitionFilter;

  public ProcessDefinitionWriterES(
      final OptimizeElasticsearchClient esClient,
      final ObjectMapper objectMapper,
      final ConfigurationService configurationService,
      final TaskRepositoryES taskRepositoryES,
      final DeletedProcessDefinitionFilter deletedProcessDefinitionFilter) {
    super(objectMapper, esClient, taskRepositoryES);
    this.configurationService = configurationService;
    this.deletedProcessDefinitionFilter = deletedProcessDefinitionFilter;
  }

  @Override
  public void importProcessDefinitions(final List<ProcessDefinitionOptimizeDto> procDefs) {
    final List<ProcessDefinitionOptimizeDto> filteredProcDefs =
        deletedProcessDefinitionFilter.filterOutSuppressed(
            procDefs, ProcessDefinitionOptimizeDto::getId);
    LOG.debug("Writing [{}] process definitions to elasticsearch", filteredProcDefs.size());
    writeProcessDefinitionInformation(filteredProcDefs);
  }

  @Override
  public void softDeleteDefinition(final String definitionId) {
    LOG.debug("Soft-deleting process definition with ID {}", definitionId);
    try {
      // Refresh immediately: callers rely on a subsequent search seeing this update right away
      esClient.update(
          new OptimizeUpdateRequestBuilderES<>()
              .optimizeIndex(esClient, PROCESS_DEFINITION_INDEX_NAME)
              .id(definitionId)
              .script(MARK_AS_DELETED_SCRIPT)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
              .refresh(Refresh.True)
              .build(),
          Object.class);
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(
          String.format(
              "There was a problem when trying to soft-delete process definition with ID %s",
              definitionId),
          e);
    }
  }

  @Override
  public void markDefinitionKeysAsOnboarded(final Set<String> definitionKeys) {
    taskRepositoryES.tryUpdateByQueryRequest(
        "process definitions onboarded state",
        MARK_AS_ONBOARDED_SCRIPT,
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.must(
                            m ->
                                m.terms(
                                    t ->
                                        t.field(PROCESS_DEFINITION_KEY)
                                            .terms(
                                                tt ->
                                                    tt.value(
                                                        definitionKeys.stream()
                                                            .map(FieldValue::of)
                                                            .toList())))))),
        PROCESS_DEFINITION_INDEX_NAME);
  }

  @Override
  Script createUpdateScript(final ProcessDefinitionOptimizeDto processDefinitionDto) {
    return ElasticsearchWriterUtil.createFieldUpdateScript(
        FIELDS_TO_UPDATE, processDefinitionDto, objectMapper);
  }

  private void writeProcessDefinitionInformation(
      final List<ProcessDefinitionOptimizeDto> procDefs) {
    final String importItemName = "process definition information";
    LOG.debug("Writing [{}] {} to ES.", procDefs.size(), importItemName);

    esClient.doImportBulkRequestWithList(
        importItemName,
        procDefs,
        this::addImportProcessDefinitionToRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
