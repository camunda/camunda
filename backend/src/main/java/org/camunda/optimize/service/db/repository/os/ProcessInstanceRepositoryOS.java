/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.os;

import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.lang.String.format;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.json;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.matchAll;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.term;
import static org.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

@Component
@RequiredArgsConstructor
@Conditional(OpenSearchCondition.class)
class ProcessInstanceRepositoryOS implements ProcessInstanceRepository {
  private final ConfigurationService configurationService;
  private final OptimizeOpenSearchClient osClient;

  @Override
  public void updateProcessInstanceStateForProcessDefinitionId(
    final String importItemName,
    final String definitionKey,
    final String processDefinitionId,
    final String state
  ) {
    osClient.updateByQueryTask(
      format("%s with %s: %s", importItemName, ProcessInstanceDto.Fields.processDefinitionId, processDefinitionId),
      createUpdateStateScript(state),
      term(ProcessInstanceDto.Fields.processDefinitionId, processDefinitionId),
      getProcessInstanceIndexAliasName(definitionKey)
    );
  }

  @Override
  public void updateAllProcessInstancesStates(
    final String importItemName,
    final String definitionKey,
    final String state
  ) {
    osClient.updateByQueryTask(
      format("%s with %s: %s", importItemName, ProcessInstanceDto.Fields.processDefinitionKey, definitionKey),
      createUpdateStateScript(state),
      matchAll(),
      getProcessInstanceIndexAliasName(definitionKey)
    );
  }

  @Override
  public void doImportBulkRequestWithList(final String importItemName, final List<ProcessInstanceDto> processInstanceDtos) {
    osClient.doImportBulkRequestWithList(
      importItemName,
      processInstanceDtos,
      dto -> UpdateOperation.<ProcessInstanceDto>of(operation ->
                                                      operation.index(
                                                        getProcessInstanceIndexAliasName(dto.getProcessDefinitionKey())
                                                        )
                                                        .id(dto.getProcessInstanceId())
                                                        .script(createUpdateStateScript(dto.getState()))
                                                        .upsert(dto)
                                                        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
      )._toBulkOperation(),
      configurationService.getSkipDataAfterNestedDocLimitReached(),
      importItemName
    );
  }

  private Script createUpdateStateScript(final String newState) {
    final ImmutableMap<String, JsonData> scriptParameters = createUpdateStateScriptParamsMap(newState);
    return createDefaultScriptWithPrimitiveParams(createInlineUpdateScript(), scriptParameters);
  }

  private ImmutableMap<String, JsonData> createUpdateStateScriptParamsMap(final String newState) {
    return ImmutableMap.of(
      "activeState", json(ACTIVE_STATE),
      "suspendedState", json(SUSPENDED_STATE),
      "newState", json(newState)
    );
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
}
