/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Script;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ProcessDefinitionWriterOS extends AbstractProcessDefinitionWriterOS implements ProcessDefinitionWriter {

  private final ConfigurationService configurationService;

  public ProcessDefinitionWriterOS(final OptimizeOpenSearchClient osClient,
                                   final ObjectMapper objectMapper,
                                   final ConfigurationService configurationService) {
    super(objectMapper, osClient);
    this.configurationService = configurationService;
  }

  @Override
  public void importProcessDefinitions(final List<ProcessDefinitionOptimizeDto> procDefs) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void markDefinitionAsDeleted(final String definitionId) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public boolean markRedeployedDefinitionsAsDeleted(final List<ProcessDefinitionOptimizeDto> importedDefinitions) {
    //todo will be handled in the OPT-7376
    return false;
  }

  @Override
  public void markDefinitionKeysAsOnboarded(final Set<String> definitionKeys) {
    //todo will be handled in the OPT-7376
  }

  @Override
  Script createUpdateScript(final ProcessDefinitionOptimizeDto processDefinitionDtos) {
    //todo will be handled in the OPT-7376
    return null;
  }

}
