package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.DecisionDefinitionXmlFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.DecisionDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.DecisionDefinitionXmlImportService;
import org.camunda.optimize.service.es.writer.DecisionDefinitionXmlWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionXmlEngineImportMediator
  extends BackoffImportMediator<DecisionDefinitionXmlImportIndexHandler> {

  @Autowired
  private DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;

  private DecisionDefinitionXmlFetcher engineEntityFetcher;
  private DecisionDefinitionXmlImportService definitionXmlImportService;

  public DecisionDefinitionXmlEngineImportMediator(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getDecisionDefinitionXmlImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(DecisionDefinitionXmlFetcher.class, engineContext);

    definitionXmlImportService = new DecisionDefinitionXmlImportService(
      decisionDefinitionXmlWriter, elasticsearchImportJobExecutor, engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    final IdSetBasedImportPage page = importIndexHandler.getNextPage();
    if (!page.getIds().isEmpty()) {
      final List<DecisionDefinitionXmlEngineDto> entities = engineEntityFetcher.fetchXmlsForDefinitions(page);
      if (!entities.isEmpty()) {
        definitionXmlImportService.executeImport(entities);
      }
      return true;
    }
    return false;
  }

}
