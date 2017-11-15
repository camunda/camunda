package org.camunda.optimize.service.engine.importing;

import org.camunda.optimize.service.engine.importing.job.factory.ActivityInstanceEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.EngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.FinishedProcessInstanceEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.ProcessDefinitionEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.ProcessDefinitionXmlEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.StoreIndexesEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.UnfinishedProcessInstanceEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.VariableInstanceEngineImportJobFactory;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
public class EngineImportBuilder {

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  @Autowired
  private EngineImportJobExecutor engineImportJobExecutor;

  private List<EngineImportJobScheduler> schedulers;

  @PostConstruct
  public void init() {
    schedulers = new ArrayList<>();
    schedulers.add(buildScheduler());
  }

  public EngineImportJobExecutor getEngineImportJobExecutor() {
    return engineImportJobExecutor;
  }

  public List<EngineImportJobScheduler> getImportSchedulers() {
    return schedulers;
  }

  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  public EngineImportJobScheduler buildScheduler() {
    List<EngineImportJobFactory> factories = createFactoryList();
    EngineImportJobScheduler scheduler =
      new EngineImportJobScheduler(
        engineImportJobExecutor,
        factories
      );
    return scheduler;
  }

  @Autowired
  private ActivityInstanceEngineImportJobFactory activityInstanceEngineImportJobFactory;

  @Autowired
  private FinishedProcessInstanceEngineImportJobFactory finishedProcessInstanceEngineImportJobFactory;

  @Autowired
  private ProcessDefinitionEngineImportJobFactory processDefinitionEngineImportJobFactory;

  @Autowired
  private ProcessDefinitionXmlEngineImportJobFactory processDefinitionXmlEngineImportJobFactory;

  @Autowired
  private StoreIndexesEngineImportJobFactory storeIndexesEngineImportJobFactory;

  @Autowired
  private UnfinishedProcessInstanceEngineImportJobFactory unfinishedProcessInstanceEngineImportJobFactory;

  @Autowired
  private VariableInstanceEngineImportJobFactory variableInstanceEngineImportJobFactory;

  private List<EngineImportJobFactory> createFactoryList() {
    List<EngineImportJobFactory> factories = new ArrayList<>();
    factories.add(activityInstanceEngineImportJobFactory);
    factories.add(finishedProcessInstanceEngineImportJobFactory);
    factories.add(processDefinitionEngineImportJobFactory);
    factories.add(processDefinitionXmlEngineImportJobFactory);
    factories.add(storeIndexesEngineImportJobFactory);
    factories.add(unfinishedProcessInstanceEngineImportJobFactory);
    factories.add(variableInstanceEngineImportJobFactory);

    factories.forEach(fac -> fac.setElasticsearchImportExecutor(elasticsearchImportJobExecutor));

    return factories;
  }
}
