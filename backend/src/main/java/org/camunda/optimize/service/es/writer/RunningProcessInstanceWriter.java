/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.DATA_SOURCE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.TENANT_ID;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
@Slf4j
public class RunningProcessInstanceWriter extends AbstractProcessInstanceWriter {
  private static final Set<String> UPDATABLE_FIELDS = Set.of(
    PROCESS_DEFINITION_KEY, PROCESS_DEFINITION_VERSION, PROCESS_DEFINITION_ID,
    BUSINESS_KEY, START_DATE, STATE,
    DATA_SOURCE, TENANT_ID
  );
  private static final String IMPORT_ITEM_NAME = "running process instances";

  private final ConfigurationService configurationService;

  public RunningProcessInstanceWriter(final OptimizeElasticsearchClient esClient,
                                      final ObjectMapper objectMapper,
                                      final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                      final ConfigurationService configurationService) {
    super(esClient, elasticSearchSchemaManager, objectMapper);
    this.configurationService = configurationService;
  }

  public List<ImportRequestDto> generateProcessInstanceImports(List<ProcessInstanceDto> processInstanceDtos) {
    log.debug("Creating imports for {} [{}].", processInstanceDtos.size(), IMPORT_ITEM_NAME);
    createInstanceIndicesIfMissing(processInstanceDtos, ProcessInstanceDto::getProcessDefinitionKey);

    return processInstanceDtos.stream()
      .map(instance -> ImportRequestDto.builder()
        .importName(IMPORT_ITEM_NAME)
        .esClient(esClient)
        .request(createImportRequestForProcessInstance(instance, UPDATABLE_FIELDS))
        .build())
      .collect(toList());
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public void importProcessInstancesFromUserOperationLogs(final List<ProcessInstanceDto> processInstanceDtos) {
    log.debug(
      "Writing changes from user operation logs to [{}] {} to ES.",
      processInstanceDtos.size(),
      IMPORT_ITEM_NAME
    );

    final List<ProcessInstanceDto> processInstanceDtoToUpdateList = processInstanceDtos.stream()
      .filter(procInst -> procInst.getProcessInstanceId() != null)
      .collect(toList());
    createInstanceIndicesIfMissing(processInstanceDtos, ProcessInstanceDto::getProcessDefinitionKey);

    ElasticsearchWriterUtil.doImportBulkRequestWithList(
      esClient,
      IMPORT_ITEM_NAME,
      processInstanceDtoToUpdateList,
      (request, dto) -> addImportProcessInstanceRequest(
        request,
        dto,
        createUpdateStateScript(dto.getState()),
        objectMapper
      ),
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  public void importProcessInstancesForProcessDefinitionIds(
    final Map<String, Map<String, String>> definitionKeyToIdToStateMap) {
    for (Map.Entry<String, Map<String, String>> definitionKeyEntry : definitionKeyToIdToStateMap.entrySet()) {
      final String definitionKey = definitionKeyEntry.getKey();
      for (Map.Entry<String, String> definitionStateEntry : definitionKeyToIdToStateMap.get(definitionKey).entrySet()) {
        createInstanceIndicesIfMissing(Sets.newHashSet((definitionKey)));
        ElasticsearchWriterUtil.tryUpdateByQueryRequest(
          esClient,
          String.format(
            "%s with %s: %s",
            IMPORT_ITEM_NAME,
            ProcessInstanceDto.Fields.processDefinitionId,
            definitionStateEntry.getKey()
          ),
          createUpdateStateScript(definitionStateEntry.getValue()),
          termsQuery(ProcessInstanceDto.Fields.processDefinitionId, definitionStateEntry.getKey()),
          getProcessInstanceIndexAliasName(definitionKey)
        );
      }
    }
  }

  public void importProcessInstancesForProcessDefinitionKeys(
    final Map<String, String> definitionKeyToNewStateMap) {
    createInstanceIndicesIfMissing(definitionKeyToNewStateMap.keySet());
    for (Map.Entry<String, String> definitionStateEntry : definitionKeyToNewStateMap.entrySet()) {
      ElasticsearchWriterUtil.tryUpdateByQueryRequest(
        esClient,
        String.format(
          "%s with %s: %s",
          IMPORT_ITEM_NAME,
          ProcessInstanceDto.Fields.processDefinitionKey,
          definitionStateEntry.getKey()
        ),
        createUpdateStateScript(definitionStateEntry.getValue()),
        matchAllQuery(),
        getProcessInstanceIndexAliasName(definitionStateEntry.getKey())
      );
    }
  }

  private Script createUpdateStateScript(final String newState) {
    final ImmutableMap<String, Object> scriptParameters = createUpdateStateScriptParamsMap(newState);
    return createDefaultScriptWithPrimitiveParams(createInlineUpdateScript(), scriptParameters);
  }

  private String createInlineUpdateScript() {
    // @formatter:off
    return
      "if ((ctx._source.state == params.activeState || ctx._source.state == params.suspendedState) " +
        "&& (params.newState.equals(params.activeState) || params.newState.equals(params.suspendedState))) {" +
        "ctx._source.state = params.newState;" +
        "}\n"
      ;
    // @formatter:on
  }

  private ImmutableMap<String, Object> createUpdateStateScriptParamsMap(final String newState) {
    return ImmutableMap.of(
      "activeState", ACTIVE_STATE,
      "suspendedState", SUSPENDED_STATE,
      "newState", newState
    );
  }
}