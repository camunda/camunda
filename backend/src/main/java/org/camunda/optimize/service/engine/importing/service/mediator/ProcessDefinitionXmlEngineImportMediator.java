package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionXmlFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlTrackingType.PROCESS_DEFINITION_XML_TRACKING_TYPE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlEngineImportMediator
    extends BackoffImportMediator<ProcessDefinitionXmlImportIndexHandler> {

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

    MissingEntitiesFinder<ProcessDefinitionXmlEngineDto> missingEntitiesFinder =
      new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        PROCESS_DEFINITION_XML_TRACKING_TYPE
      );
    definitionXmlImportService = new ProcessDefinitionXmlImportService(
      processDefinitionXmlWriter,
      elasticsearchImportJobExecutor,
      missingEntitiesFinder,
      engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    Optional<IdSetBasedImportPage> page = importIndexHandler.getNextPage();
    if (page.isPresent() && !page.get().getIds().isEmpty()) {
      List<ProcessDefinitionXmlEngineDto> entities =  engineEntityFetcher.fetchEngineEntities(page.get());
      if (!entities.isEmpty()) {
        definitionXmlImportService.executeImport(entities);
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasNewPage() {
    return importIndexHandler.hasNewPage();
  }

}
