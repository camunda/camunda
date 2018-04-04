package org.camunda.optimize.service.engine.importing;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.service.mediator.ActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.EngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.FinishedProcessInstanceEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.ProcessDefinitionEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.ProcessDefinitionXmlEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.StoreIndexesEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.UnfinishedProcessInstanceEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.VariableInstanceEngineImportMediator;
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
  private Logger logger = LoggerFactory.getLogger(getClass());

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
            mediators
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

    mediators.add(
        beanHelper.getInstance(ActivityInstanceEngineImportMediator.class, engineContext));
    mediators.add(
        beanHelper.getInstance(FinishedProcessInstanceEngineImportMediator.class, engineContext));
    mediators.add(
        beanHelper.getInstance(ProcessDefinitionEngineImportMediator.class, engineContext));
    mediators.add(
        beanHelper.getInstance(ProcessDefinitionXmlEngineImportMediator.class, engineContext));
    mediators.add(
        beanHelper.getInstance(StoreIndexesEngineImportMediator.class, engineContext));
    mediators.add(
        beanHelper.getInstance(UnfinishedProcessInstanceEngineImportMediator.class, engineContext));
    mediators.add(
        beanHelper.getInstance(VariableInstanceEngineImportMediator.class, engineContext));

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
