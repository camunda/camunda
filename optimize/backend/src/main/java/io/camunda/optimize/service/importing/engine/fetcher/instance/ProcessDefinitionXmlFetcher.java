/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.fetcher.instance;

import static io.camunda.optimize.service.util.importing.EngineConstants.PROCESS_DEFINITION_XML_ENDPOINT_TEMPLATE;

import io.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlFetcher
    extends AbstractDefinitionXmlFetcher<ProcessDefinitionXmlEngineDto> {

  private final ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionXmlFetcher(
      final EngineContext engineContext, final ProcessDefinitionWriter processDefinitionWriter) {
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
