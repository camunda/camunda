/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.db.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class RunningProcessInstanceWriterOS extends AbstractProcessInstanceWriterOS implements RunningProcessInstanceWriter {

  private final ConfigurationService configurationService;

  public RunningProcessInstanceWriterOS(final OptimizeOpenSearchClient osClient,
                                        final ObjectMapper objectMapper,
                                        final OpenSearchSchemaManager openSearchSchemaManager,
                                        final ConfigurationService configurationService) {
    super(osClient, openSearchSchemaManager, objectMapper);
    this.configurationService = configurationService;
  }

  @Override
  public List<ImportRequestDto> generateProcessInstanceImports(final List<ProcessInstanceDto> processInstanceDtos) {
    //todo will be handled in the OPT-7376
    return new ArrayList<>();
  }

  @Override
  public void importProcessInstancesFromUserOperationLogs(final List<ProcessInstanceDto> processInstanceDtos) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void importProcessInstancesForProcessDefinitionIds(final Map<String, Map<String, String>> definitionKeyToIdToStateMap) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void importProcessInstancesForProcessDefinitionKeys(final Map<String, String> definitionKeyToNewStateMap) {
    //todo will be handled in the OPT-7376
  }

}
