/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION_TAG;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
@Slf4j
public class ProcessDefinitionWriter extends AbstractProcessDefinitionWriter {
  private static final Set<String> FIELDS_TO_UPDATE = ImmutableSet.of(
    PROCESS_DEFINITION_KEY,
    PROCESS_DEFINITION_VERSION,
    PROCESS_DEFINITION_VERSION_TAG,
    PROCESS_DEFINITION_NAME,
    ENGINE,
    TENANT_ID
  );

  private static final Script MARK_AS_DELETED_SCRIPT = new Script(
    ScriptType.INLINE,
    Script.DEFAULT_SCRIPT_LANG,
    "ctx._source.deleted = true",
    Collections.emptyMap()
  );

  public ProcessDefinitionWriter(final OptimizeElasticsearchClient esClient,
                                 final ObjectMapper objectMapper) {
    super(objectMapper, esClient);
  }

  public void importProcessDefinitions(List<ProcessDefinitionOptimizeDto> procDefs) {
    log.debug("Writing [{}] process definitions to elasticsearch", procDefs.size());
    writeProcessDefinitionInformation(procDefs);
  }

  public boolean markRedeployedDefinitionsAsDeleted(final List<ProcessDefinitionOptimizeDto> importedDefinitions) {
    final BoolQueryBuilder definitionsToDeleteQuery = boolQuery();
    importedDefinitions
      .forEach(definition -> {
        final BoolQueryBuilder matchingDefinitionQuery = boolQuery()
          .must(termQuery(PROCESS_DEFINITION_KEY, definition.getKey()))
          .must(termQuery(PROCESS_DEFINITION_VERSION, definition.getVersion()))
          .mustNot(termQuery(PROCESS_DEFINITION_ID, definition.getId()));
        if (definition.getTenantId() != null) {
          matchingDefinitionQuery.must(termQuery(DecisionDefinitionIndex.TENANT_ID, definition.getTenantId()));
        } else {
          matchingDefinitionQuery.mustNot(existsQuery(DecisionDefinitionIndex.TENANT_ID));
        }
        definitionsToDeleteQuery.should(matchingDefinitionQuery);
      });

    final boolean definitionsUpdated = ElasticsearchWriterUtil.tryUpdateByQueryRequest(
      esClient,
      "processDefinition",
      "process definition deleted",
      MARK_AS_DELETED_SCRIPT,
      definitionsToDeleteQuery,
      PROCESS_DEFINITION_INDEX_NAME
    );
    if (definitionsUpdated) {
      log.debug("Marked old definitions with new deployments as deleted");
    }
    return definitionsUpdated;
  }

  @Override
  Script createUpdateScript(final ProcessDefinitionOptimizeDto processDefinitionDto) {
    return ElasticsearchWriterUtil.createPrimitiveFieldUpdateScript(FIELDS_TO_UPDATE, processDefinitionDto);
  }

  private void writeProcessDefinitionInformation(List<ProcessDefinitionOptimizeDto> procDefs) {
    String importItemName = "process definition information";
    log.debug("Writing [{}] {} to ES.", procDefs.size(), importItemName);

    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      procDefs,
      (request, dto) -> addImportProcessDefinitionToRequest(request, dto)
    );
  }
}
