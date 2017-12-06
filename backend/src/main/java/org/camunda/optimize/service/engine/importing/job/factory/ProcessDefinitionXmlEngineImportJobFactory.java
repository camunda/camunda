package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionXmlFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.engine.importing.job.ProcessDefinitionXmlEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
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
  private ProcessDefinitionWriter processDefinitionWriter;

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
        processDefinitionWriter,
        definitionBasedImportPage,
        elasticsearchImportJobExecutor,
        missingEntitiesFinder,
        engineEntityFetcher,
        engineContext
      )
    );
  }



}
