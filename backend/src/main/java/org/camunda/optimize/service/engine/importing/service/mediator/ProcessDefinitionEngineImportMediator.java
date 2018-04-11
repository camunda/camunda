package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.ProcessDefinitionImportService;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionEngineImportMediator
  extends BackoffImportMediator<AllEntitiesBasedImportIndexHandler> {

  protected EngineContext engineContext;
  private ProcessDefinitionFetcher engineEntityFetcher;
  private ProcessDefinitionImportService definitionImportService;
  @Autowired
  private ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getProcessDefinitionImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(ProcessDefinitionFetcher.class, engineContext);
    MissingEntitiesFinder<ProcessDefinitionEngineDto> missingEntitiesFinder =
      new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getProcessDefinitionType()
      );
    definitionImportService = new ProcessDefinitionImportService(
      processDefinitionWriter,
      elasticsearchImportJobExecutor,
      missingEntitiesFinder,
      engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    AllEntitiesBasedImportPage page = importIndexHandler.getNextPage();
    List<ProcessDefinitionEngineDto> entities = engineEntityFetcher.fetchEngineEntities(page);
    if (!entities.isEmpty()) {
      definitionImportService.executeImport(entities);
      importIndexHandler.moveImportIndex(entities.size());
      return entities.size() >= configurationService.getEngineImportProcessDefinitionMaxPageSize();
    }
    return false;
  }

}
