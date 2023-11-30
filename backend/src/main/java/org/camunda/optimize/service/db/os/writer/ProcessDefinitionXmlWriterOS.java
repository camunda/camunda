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
import org.camunda.optimize.service.db.writer.ProcessDefinitionXmlWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Script;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ProcessDefinitionXmlWriterOS extends AbstractProcessDefinitionWriterOS implements ProcessDefinitionXmlWriter {

  public ProcessDefinitionXmlWriterOS(final ObjectMapper objectMapper, final OptimizeOpenSearchClient osClient) {
    super(objectMapper, osClient);
  }

  @Override
  public void importProcessDefinitionXmls(final List<ProcessDefinitionOptimizeDto> processDefinitionOptimizeDtos) {

  }

  @Override
  Script createUpdateScript(final ProcessDefinitionOptimizeDto processDefinitionDtos) {
    return null;
  }

}
