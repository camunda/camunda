package org.camunda.optimize.service.engine.importing;

import org.camunda.optimize.service.engine.importing.job.factory.ActivityInstanceEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.EngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.FinishedProcessInstanceEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.ProcessDefinitionEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.ProcessDefinitionXmlEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.StoreIndexesEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.UnfinishedProcessInstanceEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.VariableInstanceEngineImportJobFactory;
import org.camunda.optimize.service.util.EngineInstanceHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EngineImportJobSchedulerFactory {

  @Autowired
  private EngineImportJobExecutor engineImportJobExecutor;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private EngineInstanceHelper engineInstanceHelper;

  private List<EngineImportJobScheduler> schedulers;


  public List<EngineImportJobScheduler> buildSchedulers() {
    List<EngineImportJobScheduler> result = new ArrayList<>();
    for (String engineAlias : this.configurationService.getConfiguredEngines().keySet()) {
      List<EngineImportJobFactory> factories = createFactoryList(engineAlias);
      EngineImportJobScheduler scheduler =
          new EngineImportJobScheduler(
              engineImportJobExecutor,
              factories,
              engineAlias
          );
      result.add(scheduler);
    }

    return result;
  }

  private List<EngineImportJobFactory> createFactoryList(String engineAlias) {
    List<EngineImportJobFactory> factories = new ArrayList<>();

    factories.add(
        engineInstanceHelper.getInstance(ActivityInstanceEngineImportJobFactory.class, engineAlias));
    factories.add(
        engineInstanceHelper.getInstance(FinishedProcessInstanceEngineImportJobFactory.class, engineAlias));
    factories.add(
        engineInstanceHelper.getInstance(ProcessDefinitionEngineImportJobFactory.class, engineAlias));
    factories.add(
        engineInstanceHelper.getInstance(ProcessDefinitionXmlEngineImportJobFactory.class, engineAlias));
    factories.add(
        engineInstanceHelper.getInstance(StoreIndexesEngineImportJobFactory.class, engineAlias));
    factories.add(
        engineInstanceHelper.getInstance(UnfinishedProcessInstanceEngineImportJobFactory.class, engineAlias));
    factories.add(
        engineInstanceHelper.getInstance(VariableInstanceEngineImportJobFactory.class, engineAlias));

    return factories;
  }

  public List<EngineImportJobScheduler> getImportSchedulers() {
    if (schedulers == null) {
      this.schedulers = this.buildSchedulers();
    }

    return schedulers;
  }
}
