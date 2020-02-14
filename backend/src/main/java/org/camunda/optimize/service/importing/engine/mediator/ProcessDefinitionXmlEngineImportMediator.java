/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;
import org.camunda.optimize.service.importing.ScrollBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.ProcessDefinitionXmlFetcher;
import org.camunda.optimize.service.importing.engine.handler.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.importing.page.IdSetBasedImportPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlEngineImportMediator
  extends ScrollBasedImportMediator<ProcessDefinitionXmlImportIndexHandler, ProcessDefinitionXmlEngineDto> {

  private ProcessDefinitionXmlFetcher engineEntityFetcher;

  @Autowired
  private ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  private final EngineContext engineContext;

  public ProcessDefinitionXmlEngineImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = importIndexHandlerRegistry.getProcessDefinitionXmlImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(ProcessDefinitionXmlFetcher.class, engineContext);

    importService = new ProcessDefinitionXmlImportService(
      elasticsearchImportJobExecutor, engineContext, processDefinitionXmlWriter
    );
  }

  @Override
  protected List<ProcessDefinitionXmlEngineDto> getEntities(final IdSetBasedImportPage page) {
    return engineEntityFetcher.fetchXmlsForDefinitions(page);
  }
}
