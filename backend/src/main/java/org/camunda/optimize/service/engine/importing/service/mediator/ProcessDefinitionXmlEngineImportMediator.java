package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionXmlFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlEngineImportMediator
    extends EngineImportMediatorImpl<ProcessDefinitionXmlImportIndexHandler> {

  private MissingEntitiesFinder<ProcessDefinitionXmlEngineDto> missingEntitiesFinder;
  private ProcessDefinitionXmlFetcher engineEntityFetcher;

  private ProcessDefinitionXmlImportService definitionXmlImportService;

  @Autowired
  private ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  protected EngineContext engineContext;

  public ProcessDefinitionXmlEngineImportMediator(EngineContext engineContext) {
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
    definitionXmlImportService = new ProcessDefinitionXmlImportService(
        processDefinitionXmlWriter,
        elasticsearchImportJobExecutor,
        missingEntitiesFinder,
        engineEntityFetcher,
        engineContext
      );
  }

  @Override
  public void importNextPage() {
    Optional<DefinitionBasedImportPage> page = importIndexHandler.getNextPage();
    page.ifPresent(definitionXmlImportService::executeImport);
  }

  @Override
  public boolean canImport() {
    return importIndexHandler.hasNewPage();
  }

  @Override
  public long getBackoffTimeInMs() {
    return importIndexHandler.getBackoffTimeInMs();
  }

}
