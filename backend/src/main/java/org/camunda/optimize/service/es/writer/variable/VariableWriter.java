/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.query.variable.value.BooleanVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.DateVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.DoubleVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.IntegerVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.LongVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.ShortVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.StringVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.VariableInstanceDto;
import org.camunda.optimize.service.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ProcessVariableHelper;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.service.util.ProcessVariableHelper.isVariableTypeSupported;
import static org.camunda.optimize.service.util.ProcessVariableHelper.variableTypeToFieldLabel;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.scriptQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@AllArgsConstructor
public abstract class VariableWriter {
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final RestHighLevelClient esClient;
  protected final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  public void importVariables(List<VariableDto> variables) throws Exception {
    logger.debug("Writing [{}] variables to elasticsearch", variables.size());

    BulkRequest addVariablesToProcessInstanceBulkRequest = new BulkRequest();

    //build map first
    Map<String, Map<String, List<VariableDto>>> processInstanceIdToTypedVariables =
      groupVariablesByProcessInstanceIds(variables);

    for (Map.Entry<String, Map<String, List<VariableDto>>> entry : processInstanceIdToTypedVariables.entrySet()) {
      //for every process id
      //for every type execute one upsert
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
        .filter(termQuery(ProcessInstanceType.PROCESS_DEFINITION_KEY, processDefinitionKey))
        .filter(rangeQuery(ProcessInstanceType.END_DATE).lt(dateTimeFormatter.format(endDate)));

      addAtLeastOneVariableArrayNotEmptyNestedFilters(filterQuery);

      UpdateByQueryRequest request = new UpdateByQueryRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
        .setQuery(filterQuery)
        .setAbortOnVersionConflict(false)
        .setScript(createVariableClearScript(ProcessVariableHelper.getAllVariableTypeFieldLabels()))
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

  private Map<String, Map<String, List<VariableDto>>> groupVariablesByProcessInstanceIds(List<VariableDto> variableUpdates) {
    Map<String, Map<String, List<VariableDto>>> processInstanceIdToTypedVariables = new HashMap<>();
    for (VariableDto variable : variableUpdates) {
      if (isVariableFromCaseDefinition(variable) || !isVariableTypeSupported(variable.getType())) {
        logger.warn(
          "Variable [{}] is either a case definition variable or the type [{}] is not supported!",
          variable, variable.getType()
        );
        continue;
      }

      if (!processInstanceIdToTypedVariables.containsKey(variable.getProcessInstanceId())) {
        processInstanceIdToTypedVariables.put(variable.getProcessInstanceId(), newTypeMap());
      }
      Map<String, List<VariableDto>> typedVars = processInstanceIdToTypedVariables.get(variable.getProcessInstanceId());

      typedVars.get(variableTypeToFieldLabel(variable.getType())).add(variable);
    }
    return processInstanceIdToTypedVariables;
  }

  private Map<String, List<VariableDto>> newTypeMap() {
    Map<String, List<VariableDto>> result = new HashMap<>();
    for (String type : ProcessVariableHelper.allVariableTypeFieldLabels) {
      result.put(type, new ArrayList<>());
    }
    return result;
  }

  private void addImportVariablesRequest(
    BulkRequest addVariablesToProcessInstanceBulkRequest,
    String processInstanceId,
    Map<String, List<VariableDto>> typeMappedVars) throws IOException {

    Map<String, Object> params = buildParameters(typeMappedVars);

    final Script updateScript = createDefaultScript(createInlineUpdateScript(typeMappedVars), params);

    String newEntryIfAbsent = getNewProcessInstanceRecordString(processInstanceId, typeMappedVars);

    if (newEntryIfAbsent != null) {
      UpdateRequest request =
        new UpdateRequest(
          getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE),
          PROC_INSTANCE_TYPE,
          processInstanceId
        )
          .script(updateScript)
          .upsert(newEntryIfAbsent, XContentType.JSON)
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      addVariablesToProcessInstanceBulkRequest.add(request);
    }

  }

  private Map<String, Object> buildParameters(Map<String, List<VariableDto>> typeMappedVars) throws IOException {
    Map<String, Object> params = new HashMap<>();
    for (Map.Entry<String, List<VariableDto>> typedVarsEntry : typeMappedVars.entrySet()) {
      String typeName = typedVarsEntry.getKey();
      List jsonMap = objectMapper.readValue(
        objectMapper.writeValueAsString(mapTypeValues(typedVarsEntry.getValue())),
        List.class
      );
      params.put(typeName, jsonMap);
    }

    return params;
  }

  private List<VariableInstanceDto> mapTypeValues(List<VariableDto> variablesOfOneType) {
    List<VariableInstanceDto> result = new ArrayList<>();
    for (VariableDto variable : variablesOfOneType) {
      parseValue(variable)
        .ifPresent(result::add);
    }
    return result;
  }

  protected abstract String createInlineUpdateScript(Map<String, List<VariableDto>> typeMappedVars);

  protected abstract String getNewProcessInstanceRecordString(String processInstanceId,
                                                              Map<String, List<VariableDto>> typeMappedVars) throws
                                                                                                             JsonProcessingException;


  protected VariableDto grabFirstOne(Map<String, List<VariableDto>> typeMappedVars) {
    VariableDto variable = null;
    for (Map.Entry<String, List<VariableDto>> e : typeMappedVars.entrySet()) {
      if (!e.getValue().isEmpty()) {
        variable = e.getValue().get(0);
        break;
      }
    }
    return variable;
  }

  private boolean isVariableFromCaseDefinition(VariableDto variable) {
    return variable.getProcessDefinitionId() == null;
  }

  protected Optional<VariableInstanceDto> parseValue(VariableDto var) {
    try {
      return Optional.of(parseValueWithException(var));
    } catch (NumberFormatException ex) {
      logger.error(
        "Could not parse variable [{}] with type [{}] and value [{}]. Skipping this variable",
        var.getName(),
        var.getType(),
        var.getValue(),
        ex
      );
      return Optional.empty();
    }
  }

  private VariableInstanceDto parseValueWithException(VariableDto var) {
    final VariableInstanceDto variableInstanceDto;
    switch (VariableType.getTypeForId(var.getType())) {
      default:
        logger.warn(
          "Unsupported variable type [{}] if variable {}! Interpreting this as string type instead.",
          var.getType(), var.getName()
        );
      case STRING:
        StringVariableDto stringVariableDto = new StringVariableDto();
        stringVariableDto.setValue(var.getValue());
        variableInstanceDto = stringVariableDto;
        break;
      case INTEGER:
        IntegerVariableDto integerVariableDto = new IntegerVariableDto();
        integerVariableDto.setValue(parseInteger(var));
        variableInstanceDto = integerVariableDto;
        break;
      case LONG:
        LongVariableDto longVariableDto = new LongVariableDto();
        longVariableDto.setValue(parseLong(var));
        variableInstanceDto = longVariableDto;
        break;
      case SHORT:
        ShortVariableDto shortVariableDto = new ShortVariableDto();
        shortVariableDto.setValue(parseShort(var));
        variableInstanceDto = shortVariableDto;
        break;
      case DOUBLE:
        DoubleVariableDto doubleVariableDto = new DoubleVariableDto();
        doubleVariableDto.setValue(parseDouble(var));
        variableInstanceDto = doubleVariableDto;
        break;
      case BOOLEAN:
        BooleanVariableDto booleanVariableDto = new BooleanVariableDto();
        booleanVariableDto.setValue(parseBoolean(var));
        variableInstanceDto = booleanVariableDto;
        break;
      case DATE:
        DateVariableDto dateVariableDto = new DateVariableDto();
        try {
          OffsetDateTime offsetDateTime = objectMapper.readerFor(OffsetDateTime.class)
            .readValue("\"" + var.getValue() + "\"");
          dateVariableDto.setValue(offsetDateTime);
        } catch (IOException error) {
          logger.debug(
            "Could not deserialize date variable out of [{}]! Reason: {}",
            var.getValue(),
            error.getMessage()
          );
        }
        variableInstanceDto = dateVariableDto;
        break;
    }

    variableInstanceDto.setType(var.getType());
    variableInstanceDto.setId(var.getId());
    variableInstanceDto.setName(var.getName());
    variableInstanceDto.setVersion(var.getVersion());
    return variableInstanceDto;
  }

  private Boolean parseBoolean(VariableDto var) {
    return var.getValue() == null ? null : Boolean.parseBoolean(var.getValue());
  }

  private Double parseDouble(VariableDto var) {
    return var.getValue() == null ? null : Double.parseDouble(var.getValue());
  }

  private Short parseShort(VariableDto var) {
    return var.getValue() == null ? null : Short.parseShort(var.getValue());
  }

  private Long parseLong(VariableDto var) {
    return var.getValue() == null ? null : Long.parseLong(var.getValue());
  }

  private Integer parseInteger(VariableDto var) {
    return var.getValue() == null ? null : Integer.parseInt(var.getValue());
  }

  private static Script createVariableClearScript(String[] variableFieldNames) {
    final StringBuilder builder = new StringBuilder();
    for (String variableField : variableFieldNames) {
      builder.append(
        MessageFormat.format("ctx._source.{0} = new ArrayList();\n", variableField)
      );
    }
    return new Script(builder.toString());
  }

  private static void addAtLeastOneVariableArrayNotEmptyNestedFilters(final BoolQueryBuilder queryBuilder) {
    final BoolQueryBuilder innerBoolQuery = boolQuery();
    innerBoolQuery.minimumShouldMatch(1);

    Arrays.stream(ProcessVariableHelper.getAllVariableTypeFieldLabels())
      .forEach(variableName -> innerBoolQuery.should(
        nestedQuery(
          variableName,
          scriptQuery(new Script(MessageFormat.format("doc[''{0}.id''].length > 0", variableName))),
          ScoreMode.None
        )
      ));

    queryBuilder.filter(innerBoolQuery);
  }

}
