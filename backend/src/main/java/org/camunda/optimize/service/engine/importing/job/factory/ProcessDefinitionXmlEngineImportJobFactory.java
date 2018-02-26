package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionXmlFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.engine.importing.job.ProcessDefinitionXmlEngineImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlEngineImportJobFactory
    extends EngineImportJobFactoryImpl<ProcessDefinitionXmlImportIndexHandler> {

  private MissingEntitiesFinder<ProcessDefinitionXmlEngineDto> missingEntitiesFinder;
  private ProcessDefinitionXmlFetcher engineEntityFetcher;

  @Autowired
  private ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  protected EngineContext engineContext;

  public ProcessDefinitionXmlEngineImportJobFactory(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getProcessDefinitionXmlImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(ProcessDefinitionXmlFetcher.class, engineContext);

    missingEntitiesFinder = new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getProcessDefinitionXmlType()
    );
  }

  @Override
  public long getBackoffTimeInMs() {
    return importIndexHandler.getBackoffTimeInMs();
  }

  public Optional<Runnable> getNextJob() {
    Optional<DefinitionBasedImportPage> page = importIndexHandler.getNextPage();
    return page.map(
      definitionBasedImportPage -> new ProcessDefinitionXmlEngineImportJob(
        processDefinitionXmlWriter,
        definitionBasedImportPage,
        elasticsearchImportJobExecutor,
        missingEntitiesFinder,
        engineEntityFetcher,
        engineContext
      )
    );
  }



}
