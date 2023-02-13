/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.util.importing.EngineConstants.PROCESS_DEFINITION_XML_ENDPOINT_TEMPLATE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlFetcher extends AbstractDefinitionXmlFetcher<ProcessDefinitionXmlEngineDto> {

  private final ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionXmlFetcher(final EngineContext engineContext, final ProcessDefinitionWriter processDefinitionWriter) {
    super(engineContext);
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  protected void markDefinitionAsDeleted(final String definitionId) {
    processDefinitionWriter.markDefinitionAsDeleted(definitionId);
  }

  @Override
  protected String getRequestPath() {
    return PROCESS_DEFINITION_XML_ENDPOINT_TEMPLATE;
  }

  @Override
  protected Class<ProcessDefinitionXmlEngineDto> getOptimizeClassForDefinitionResponse() {
    return ProcessDefinitionXmlEngineDto.class;
  }

}
