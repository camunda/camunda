/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.apache.commons.text.StringSubstitutor;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.service.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.service.util.ProcessVariableHelper.isVariableTypeSupported;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.scriptQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@AllArgsConstructor
@Component
public class ProcessVariableUpdateWriter {

  private static final String VARIABLE_UPDATES_FROM_ENGINE = "variableUpdatesFromEngine";
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final OptimizeElasticsearchClient esClient;
  protected final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  public void importVariables(List<ProcessVariableDto> variables) throws Exception {
    logger.debug("Writing [{}] variables to elasticsearch", variables.size());

    BulkRequest addVariablesToProcessInstanceBulkRequest = new BulkRequest();

    //build map first
    Map<String, List<ProcessVariableDto>> processInstanceIdToVariables =
      groupVariablesByProcessInstanceIds(variables);

    for (Map.Entry<String, List<ProcessVariableDto>> entry : processInstanceIdToVariables.entrySet()) {
      //for every process id
      addImportVariablesRequest(addVariablesToProcessInstanceBulkRequest, entry.getKey(), entry.getValue());
    }
    if (addVariablesToProcessInstanceBulkRequest.numberOfActions() != 0) {
      BulkResponse bulkResponse = esClient.bulk(addVariablesToProcessInstanceBulkRequest, RequestOptions.DEFAULT);
      if (bulkResponse.hasFailures()) {
        final String errorMessage = String.format(
          "There were failures while writing variables with message: %s", bulkResponse.buildFailureMessage()
        );
        throw new OptimizeRuntimeException(errorMessage);
      }
    }
  }

  public void deleteAllInstanceVariablesByProcessDefinitionKeyAndEndDateOlderThan(final String processDefinitionKey,
                                                                                  final OffsetDateTime endDate) {
    logger.info(
      "Deleting variables of process instances for processDefinitionKey {} and endDate past {}",
      processDefinitionKey,
      endDate
    );

    final EsBulkByScrollTaskActionProgressReporter progressReporter = new EsBulkByScrollTaskActionProgressReporter(
      getClass().getName(), esClient, UpdateByQueryAction.NAME
    );
    try {
      progressReporter.start();
      final BoolQueryBuilder filterQuery = boolQuery()
        .filter(termQuery(ProcessInstanceIndex.PROCESS_DEFINITION_KEY, processDefinitionKey))
        .filter(rangeQuery(ProcessInstanceIndex.END_DATE).lt(dateTimeFormatter.format(endDate)));

      addAtLeastOneVariableArrayNotEmptyNestedFilters(filterQuery);

      UpdateByQueryRequest request = new UpdateByQueryRequest(PROCESS_INSTANCE_INDEX_NAME)
        .setQuery(filterQuery)
        .setAbortOnVersionConflict(false)
        .setScript(createVariableClearScript())
        .setRefresh(true);

      BulkByScrollResponse bulkByScrollResponse;
      try {
        bulkByScrollResponse = esClient.updateByQuery(request, RequestOptions.DEFAULT);
      } catch (IOException e) {
        String reason =
          String.format("Could not delete process instance variables" +
                          "for process definition key [%s] and end date [%s].", processDefinitionKey, endDate);
        logger.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }

      logger.debug(
        "BulkByScrollResponse on deleting variables for processDefinitionKey {}: {}", processDefinitionKey,
        bulkByScrollResponse
      );
      logger.info(
        "Deleted variables on {} process instances for processDefinitionKey {} and endDate past {}",
        bulkByScrollResponse.getUpdated(),
        processDefinitionKey,
        endDate
      );
    } finally {
      progressReporter.stop();
    }
  }

  private Map<String, List<ProcessVariableDto>> groupVariablesByProcessInstanceIds(List<ProcessVariableDto> variableUpdates) {
    Map<String, List<ProcessVariableDto>> processInstanceIdToVariables = new HashMap<>();
    for (ProcessVariableDto variable : variableUpdates) {
      if (isVariableFromCaseDefinition(variable) || !isVariableTypeSupported(variable.getType())) {
        logger.warn(
          "Variable [{}] is either a case definition variable or the type [{}] is not supported!",
          variable, variable.getType()
        );
        continue;
      }
      processInstanceIdToVariables.putIfAbsent(variable.getProcessInstanceId(), new ArrayList<>());
      processInstanceIdToVariables.get(variable.getProcessInstanceId()).add(variable);
    }
    return processInstanceIdToVariables;
  }

  private void addImportVariablesRequest(BulkRequest addVariablesToProcessInstanceBulkRequest,
                                         String processInstanceId,
                                         List<ProcessVariableDto> variablesWitAllInformation) throws IOException {
    List<SimpleProcessVariableDto> variables = mapToSimpleVariables(variablesWitAllInformation);
    Map<String, Object> params = buildParameters(variables);
    final Script updateScript = createDefaultScript(createInlineUpdateScript(), params);

    if (variablesWitAllInformation.isEmpty()) {
      //all is lost, no variables to persist, should have crashed before.
      return;
    }
    final ProcessVariableDto firstVariable = variablesWitAllInformation.get(0);
    String newEntryIfAbsent = getNewProcessInstanceRecordString(
      processInstanceId,
      firstVariable.getEngineAlias(),
      firstVariable.getTenantId(),
      variables
    );

    if (newEntryIfAbsent != null) {
      UpdateRequest request = new UpdateRequest(
        PROCESS_INSTANCE_INDEX_NAME,
        PROCESS_INSTANCE_INDEX_NAME, processInstanceId)
        .script(updateScript)
        .upsert(newEntryIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      addVariablesToProcessInstanceBulkRequest.add(request);
    }

  }

  private List<SimpleProcessVariableDto> mapToSimpleVariables(final List<ProcessVariableDto> variablesWitAllInformation) {
    return variablesWitAllInformation
      .stream()
      .map(var -> new SimpleProcessVariableDto(
        var.getId(),
        var.getName(),
        var.getType(),
        var.getValue(),
        var.getVersion()
      ))
      .collect(Collectors.toList());
  }

  private Map<String, Object> buildParameters(final List<SimpleProcessVariableDto> variables) {
    Map<String, Object> params = new HashMap<>();
    params.put(
      VARIABLE_UPDATES_FROM_ENGINE,
      objectMapper.convertValue(variables, new TypeReference<List<Map>>(){})
    );
    return params;
  }

  private String createInlineUpdateScript() {
    StringBuilder builder = new StringBuilder();
    Map<String, String> substitutions = new HashMap<>();
    substitutions.put("variables", VARIABLES);
    substitutions.put("variableUpdatesFromEngine", VARIABLE_UPDATES_FROM_ENGINE);
    final StringSubstitutor sub = new StringSubstitutor(substitutions);
    // @formatter:off
    String variableScript =
      "HashMap varIdToVar = new HashMap();" +
      "for (def var : ctx._source.${variables}) {" +
        "varIdToVar.put(var.id, var);" +
      "}" +
      "for (def var : params.${variableUpdatesFromEngine}) {" +
        "varIdToVar.compute(var.id, (k, v) -> { " +
        "  if (v == null) {" +
        "    return var;"   +
        "  } else {" +
        "    return v.version > var.version? v : var;" +
        "  }" +
        "});" +
      "}" +
      "ctx._source.${variables} = varIdToVar.values();\n";
    // @formatter:on
    String resolvedVariableScript = sub.replace(variableScript);
    builder.append(resolvedVariableScript);
    return builder.toString();
  }

  private String getNewProcessInstanceRecordString(final String processInstanceId,
                                                   final String engineAlias,
                                                   final String tenantId,
                                                   final List<SimpleProcessVariableDto> variables)
    throws JsonProcessingException {
    final ProcessInstanceDto procInst = new ProcessInstanceDto()
      .setProcessInstanceId(processInstanceId)
      .setEngine(engineAlias)
      .setTenantId(tenantId);
    procInst.getVariables().addAll(variables);

    return objectMapper.writeValueAsString(procInst);
  }

  private boolean isVariableFromCaseDefinition(ProcessVariableDto variable) {
    return variable.getProcessDefinitionId() == null;
  }

  private static Script createVariableClearScript() {
    String script = MessageFormat.format("ctx._source.{0} = new ArrayList();\n", VARIABLES);
    return new Script(script);
  }

  private static void addAtLeastOneVariableArrayNotEmptyNestedFilters(final BoolQueryBuilder queryBuilder) {
    final BoolQueryBuilder innerBoolQuery = boolQuery();
    innerBoolQuery.must(
      nestedQuery(
        VARIABLES,
        scriptQuery(new Script(MessageFormat.format("doc[''{0}.id''].length > 0", VARIABLES))),
        ScoreMode.None
      )
    );
  }

}
