/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerProvider;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.factory.ActivityInstanceEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.engine.mediator.factory.DecisionDefinitionEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.engine.mediator.factory.DecisionDefinitionInstanceEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.engine.mediator.factory.DecisionDefinitionXmlEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.engine.mediator.factory.IdentityLinkLogEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.engine.mediator.factory.ProcessDefinitionEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.engine.mediator.factory.ProcessDefinitionXmlEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.engine.mediator.factory.ProcessInstanceEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.engine.mediator.factory.StoreIndexesEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.engine.mediator.factory.TenantImportMediatorFactory;
import org.camunda.optimize.service.importing.engine.mediator.factory.UserOperationLogEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.engine.mediator.factory.UserTaskInstanceEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.engine.mediator.factory.VariableUpdateEngineImportMediatorFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class EngineImportSchedulerFactory implements ConfigurationReloadable {
  private static final Logger logger = LoggerFactory.getLogger(EngineImportSchedulerFactory.class);

  private final EngineImportIndexHandlerRegistry importIndexHandlerRegistry;
  private final BeanFactory beanFactory;
  private final EngineContextFactory engineContextFactory;
  private final ConfigurationService configurationService;

  private final TenantImportMediatorFactory tenantImportMediatorFactory;
  private final ProcessInstanceEngineImportMediatorFactory processInstanceEngineImportMediatorFactory;
  private final ProcessDefinitionEngineImportMediatorFactory processDefinitionEngineImportMediatorFactory;
  private final ProcessDefinitionXmlEngineImportMediatorFactory processDefinitionXmlImportMediatorFactory;
  private final DecisionDefinitionEngineImportMediatorFactory decisionDefinitionEngineImportMediatorFactory;
  private final DecisionDefinitionXmlEngineImportMediatorFactory decisionDefinitionXmlEngineImportMediatorFactory;
  private final DecisionDefinitionInstanceEngineImportMediatorFactory decisionDefinitionInstanceEngineImportMediatorFactory;
  private final ActivityInstanceEngineImportMediatorFactory activityInstanceEngineImportMediatorFactory;
  private final UserTaskInstanceEngineImportMediatorFactory userTaskInstanceEngineImportMediatorFactory;
  private final StoreIndexesEngineImportMediatorFactory storeIndexesEngineImportMediatorFactory;
  private final VariableUpdateEngineImportMediatorFactory variableUpdateEngineImportMediatorFactory;
  private final IdentityLinkLogEngineImportMediatorFactory identityLinkLogEngineImportMediatorFactory;
  private final UserOperationLogEngineImportMediatorFactory userOperationLogEngineImportMediatorFactory;

  private List<EngineImportScheduler> schedulers;

  private List<EngineImportScheduler> buildSchedulers() {
    final List<EngineImportScheduler> result = new ArrayList<>();
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      try {
        final List<EngineImportMediator> mediators = createMediatorList(engineContext);
        final EngineImportScheduler scheduler = new EngineImportScheduler(mediators, engineContext.getEngineAlias());
        result.add(scheduler);
      } catch (Exception e) {
        logger.error("Can't create scheduler for engine [{}]", engineContext.getEngineAlias(), e);
      }
    }

    return result;
  }

  private List<EngineImportMediator> createMediatorList(EngineContext engineContext) {
    List<EngineImportMediator> mediators = new ArrayList<>();
    importIndexHandlerRegistry.register(
      engineContext.getEngineAlias(),
      beanFactory.getBean(EngineImportIndexHandlerProvider.class, engineContext)
    );

    // tenant import first
    mediators.add(
      tenantImportMediatorFactory.createTenantImportMediator(engineContext)
    );

    // definition imports come first in line,
    mediators.add(
      processDefinitionEngineImportMediatorFactory.createProcessDefinitionEngineImportMediator(engineContext)
    );
    mediators.add(
      processDefinitionXmlImportMediatorFactory.createProcessDefinitionXmlEngineImportMediator(engineContext)
    );
    if (configurationService.getImportDmnDataEnabled()) {
      mediators.add(
        decisionDefinitionEngineImportMediatorFactory.createDecisionDefinitionEngineImportMediator(engineContext)
      );
      mediators.add(
        decisionDefinitionXmlEngineImportMediatorFactory.createDecisionDefinitionXmlEngineImportMediator(engineContext)
      );
    }

    // so potential dependencies by the instance and activity import on existing definition data are likely satisfied
    mediators.add(
      activityInstanceEngineImportMediatorFactory.createCompletedActivityInstanceEngineImportMediator(engineContext)
    );
    mediators.add(
      activityInstanceEngineImportMediatorFactory.createRunningActivityInstanceEngineImportMediator(engineContext)
    );
    mediators.add(
      processInstanceEngineImportMediatorFactory.createCompletedProcessInstanceEngineImportMediator(engineContext)
    );
    mediators.add(
      processInstanceEngineImportMediatorFactory.createRunningProcessInstanceEngineImportMediator(engineContext)
    );
    mediators.add(
      storeIndexesEngineImportMediatorFactory.createStoreIndexImportMediator(engineContext)
    );
    mediators.add(
      variableUpdateEngineImportMediatorFactory.createVariableUpdateEngineImportMediator(engineContext)
    );
    mediators.add(
      userTaskInstanceEngineImportMediatorFactory.createCompletedUserTaskInstanceEngineImportMediator(engineContext)
    );
    if (configurationService.getImportUserTaskWorkerDataEnabled()) {
      mediators.add(
        identityLinkLogEngineImportMediatorFactory.createIdentityLinkLogEngineImportMediator(engineContext)
      );
    }
    mediators.add(
      userTaskInstanceEngineImportMediatorFactory.createRunningUserTaskInstanceEngineImportMediator(engineContext)
    );
    mediators.add(
      userOperationLogEngineImportMediatorFactory.createUserOperationLogEngineImportMediator(engineContext)
    );

    if (configurationService.getImportDmnDataEnabled()) {
      mediators.add(
        decisionDefinitionInstanceEngineImportMediatorFactory.createDecisionInstanceEngineImportMediator(engineContext)
      );
    }

    return mediators;
  }

  public List<EngineImportScheduler> getImportSchedulers() {
    if (schedulers == null) {
      this.schedulers = this.buildSchedulers();
    }
    return schedulers;
  }

  @PreDestroy
  public void shutdown() {
    if (schedulers != null) {
      for (EngineImportScheduler oldScheduler : schedulers) {
        oldScheduler.stopImportScheduling();
        oldScheduler.shutdown();
      }
    }
    engineContextFactory.close();
  }

  @Override
  public void reloadConfiguration(ApplicationContext context) {
    shutdown();
    engineContextFactory.init();
    importIndexHandlerRegistry.reloadConfiguration();
    schedulers = this.buildSchedulers();
  }
}
