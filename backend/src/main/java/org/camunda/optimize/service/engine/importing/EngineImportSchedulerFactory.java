package org.camunda.optimize.service.engine.importing;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.service.mediator.CompletedActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.CompletedProcessInstanceEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.DecisionDefinitionEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.DecisionDefinitionXmlEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.DecisionInstanceEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.EngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.ProcessDefinitionEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.ProcessDefinitionXmlEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.RunningActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.RunningProcessInstanceEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.StoreIndexesEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.VariableUpdateEngineImportMediator;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EngineImportSchedulerFactory implements ConfigurationReloadable {
  private static final Logger logger = LoggerFactory.getLogger(EngineImportSchedulerFactory.class);

  @Autowired
  private ImportIndexHandlerProvider importIndexHandlerProvider;

  @Autowired
  private BeanHelper beanHelper;
  @Autowired
  private EngineContextFactory engineContextFactory;

  private List<EngineImportScheduler> schedulers;


  public List<EngineImportScheduler> buildSchedulers() {
    List<EngineImportScheduler> result = new ArrayList<>();
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      try {
        List<EngineImportMediator> mediators = createMediatorList(engineContext);
        EngineImportScheduler scheduler = new EngineImportScheduler(
          mediators,
          engineContext.getEngineAlias()
        );
        result.add(scheduler);
      } catch (Exception e) {
        logger.error("Can't create scheduler for engine [{}]", engineContext.getEngineAlias(), e);
      }

    }

    return result.isEmpty() ? null : result;
  }

  private List<EngineImportMediator> createMediatorList(EngineContext engineContext) {
    List<EngineImportMediator> mediators = new ArrayList<>();
    importIndexHandlerProvider.init(engineContext);

    // definition imports come first in line,
    mediators.add(
      beanHelper.getInstance(ProcessDefinitionEngineImportMediator.class, engineContext)
    );
    mediators.add(
      beanHelper.getInstance(ProcessDefinitionXmlEngineImportMediator.class, engineContext)
    );
    mediators.add(
      beanHelper.getInstance(DecisionDefinitionEngineImportMediator.class, engineContext)
    );
    mediators.add(
      beanHelper.getInstance(DecisionDefinitionXmlEngineImportMediator.class, engineContext)
    );

    // so potential dependencies by the instance and activity import on existing definition data are likely satisfied
    mediators.add(
      beanHelper.getInstance(CompletedActivityInstanceEngineImportMediator.class, engineContext));
    mediators.add(
      beanHelper.getInstance(RunningActivityInstanceEngineImportMediator.class, engineContext));
    mediators.add(
      beanHelper.getInstance(CompletedProcessInstanceEngineImportMediator.class, engineContext));
    mediators.add(
      beanHelper.getInstance(StoreIndexesEngineImportMediator.class, engineContext));
    mediators.add(
      beanHelper.getInstance(RunningProcessInstanceEngineImportMediator.class, engineContext));
    mediators.add(
      beanHelper.getInstance(VariableUpdateEngineImportMediator.class, engineContext));
    mediators.add(
      beanHelper.getInstance(DecisionInstanceEngineImportMediator.class, engineContext));

    return mediators;
  }

  public List<EngineImportScheduler> getImportSchedulers() {
    if (schedulers == null) {
      this.schedulers = this.buildSchedulers();
    }

    return schedulers;
  }

  @Override
  public void reloadConfiguration(ApplicationContext context) {
    if (schedulers != null) {
      for (EngineImportScheduler oldScheduler : schedulers) {
        oldScheduler.disable();
      }
    }
    engineContextFactory.init();
    importIndexHandlerProvider.reloadConfiguration();
    schedulers = this.buildSchedulers();
  }
}
