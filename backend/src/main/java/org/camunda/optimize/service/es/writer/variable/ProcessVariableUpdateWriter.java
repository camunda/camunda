/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer.variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.writer.AbstractProcessInstanceDataWriter;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.security.InvalidParameterException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.service.util.VariableHelper.isProcessVariableTypeSupported;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

@Component
@Slf4j
public class ProcessVariableUpdateWriter extends AbstractProcessInstanceDataWriter<ProcessVariableDto> {

  private static final Script VARIABLE_CLEAR_SCRIPT = new Script(
    MessageFormat.format("ctx._source.{0} = new ArrayList();\n", VARIABLES)
  );
  private static final String VARIABLE_UPDATES_FROM_ENGINE = "variableUpdatesFromEngine";

  private final ObjectMapper objectMapper;

  public ProcessVariableUpdateWriter(final OptimizeElasticsearchClient esClient,
                                     final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                     final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager);
    this.objectMapper = objectMapper;
  }


  public List<ImportRequestDto> generateVariableUpdateImports(List<ProcessVariableDto> variables) {
    String importItemName = "variables";
    log.debug("Creating imports for {} [{}].", variables.size(), importItemName);

    createInstanceIndicesIfMissing(variables, ProcessVariableDto::getProcessDefinitionKey);

    Map<String, List<OptimizeDto>> processInstanceIdToVariables = groupVariablesByProcessInstanceIds(variables);

    return processInstanceIdToVariables.entrySet().stream()
      .map(entry -> ImportRequestDto.builder()
        .importName(importItemName)
        .esClient(esClient)
        .request(createUpdateRequestForProcessInstanceVariables(entry))
        .build())
      .filter(update -> Objects.nonNull(update.getRequest()))
      .collect(Collectors.toList());
  }

  private UpdateRequest createUpdateRequestForProcessInstanceVariables(
    final Map.Entry<String, List<OptimizeDto>> processInstanceIdToVariables) {
    if (!(processInstanceIdToVariables.getValue().stream().allMatch(ProcessVariableDto.class::isInstance))) {
      throw new InvalidParameterException("Method called with incorrect instance of DTO.");
    }
    final List<ProcessVariableDto> variablesWithAllInformation =
      (List<ProcessVariableDto>) (List<?>) processInstanceIdToVariables.getValue();
    final String processInstanceId = processInstanceIdToVariables.getKey();
    final String processDefinitionKey = variablesWithAllInformation.get(0).getProcessDefinitionKey();

    List<SimpleProcessVariableDto> variables = mapToSimpleVariables(variablesWithAllInformation);
    Map<String, Object> params = buildParameters(variables);

    final Script updateScript = createDefaultScriptWithSpecificDtoParams(
      createInlineUpdateScript(),
      params,
      objectMapper
    );

    if (variablesWithAllInformation.isEmpty()) {
      // all is lost, no variables to persist, should have crashed before.
      return null;
    }
    final ProcessVariableDto firstVariable = variablesWithAllInformation.get(0);
    String newEntryIfAbsent;
    try {
      newEntryIfAbsent = getNewProcessInstanceRecordString(
        processInstanceId,
        firstVariable.getEngineAlias(),
        firstVariable.getTenantId(),
        variables
      );
    } catch (JsonProcessingException e) {
      String reason = String.format(
        "Error while processing JSON for activity instances with ID [%s].",
        processInstanceId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (newEntryIfAbsent != null) {
      return new UpdateRequest()
        .index(getProcessInstanceIndexAliasName(processDefinitionKey))
        .id(processInstanceId)
        .script(updateScript)
        .upsert(newEntryIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    }
    return null;
  }

  public void deleteVariableDataByProcessInstanceIds(final String processDefinitionKey,
                                                     final List<String> processInstanceIds) {
    final BulkRequest bulkRequest = new BulkRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
    log.debug(
      "Deleting variable data on [{}] process instance documents with bulk request.",
      processInstanceIds.size()
    );
    processInstanceIds.forEach(
      id -> bulkRequest.add(
        new UpdateRequest(getProcessInstanceIndexAliasName(processDefinitionKey), id)
          .script(VARIABLE_CLEAR_SCRIPT)
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
      )
    );
    ElasticsearchWriterUtil.doBulkRequest(
      esClient,
      bulkRequest,
      getProcessInstanceIndexAliasName(processDefinitionKey),
      false
    );
  }

  private Map<String, List<OptimizeDto>> groupVariablesByProcessInstanceIds(List<ProcessVariableDto> variableUpdates) {
    Map<String, List<OptimizeDto>> processInstanceIdToVariables = new HashMap<>();
    for (ProcessVariableDto variable : variableUpdates) {
      if (isVariableFromCaseDefinition(variable) || !isProcessVariableTypeSupported(variable.getType())) {
        log.warn(
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

  private List<SimpleProcessVariableDto> mapToSimpleVariables(final List<ProcessVariableDto> variablesWithAllInformation) {
    return variablesWithAllInformation
      .stream()
      .map(var -> new SimpleProcessVariableDto(
        var.getId(),
        var.getName(),
        var.getType(),
        var.getValue(),
        var.getVersion()
      ))
      .map(variable -> {
        if (variable.getValue().stream().allMatch(Objects::isNull)) {
          variable.setValue(Collections.emptyList());
        }
        return variable;
      })
      .collect(Collectors.toList());
  }

  private Map<String, Object> buildParameters(final List<SimpleProcessVariableDto> variables) {
    Map<String, Object> params = new HashMap<>();
    params.put(VARIABLE_UPDATES_FROM_ENGINE, variables);
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
      "for (def existingVar : ctx._source.${variables}) {" +
        "varIdToVar.put(existingVar.id, existingVar);" +
      "}" +
      "for (def newVar : params.${variableUpdatesFromEngine}) {" +
        "varIdToVar.compute(newVar.id, (k, v) -> { " +
        "  if (v == null) {" +
        "    return newVar;"   +
        "  } else {" +
        "    return v.version > newVar.version ? v : newVar;" +
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
    final ProcessInstanceDto procInst = ProcessInstanceDto.builder()
      .processInstanceId(processInstanceId)
      .dataSource(new EngineDataSourceDto(engineAlias))
      .tenantId(tenantId)
      .build();
    procInst.getVariables().addAll(variables);

    return objectMapper.writeValueAsString(procInst);
  }

  private boolean isVariableFromCaseDefinition(final ProcessVariableDto variable) {
    // if the variable instance is not related to a process instance we assume it's originating from a case definition
    return variable.getProcessInstanceId() == null;
  }

}
