package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.FinalVariableInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.FinalVariableInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.FinalVariableInstanceImportService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.variable.FinalVariableInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinalVariableInstanceEngineImportMediator
    extends BackoffImportMediator<FinalVariableInstanceImportIndexHandler> {

  private FinalVariableInstanceFetcher engineEntityFetcher;
  private FinalVariableInstanceImportService finalVariableInstanceImportService;

  @Autowired
  private FinalVariableInstanceWriter variableWriter;
  @Autowired
  private ImportAdapterProvider importAdapterProvider;
  @Autowired
  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  protected EngineContext engineContext;

  public FinalVariableInstanceEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getVariableInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(FinalVariableInstanceFetcher.class, engineContext);
    finalVariableInstanceImportService = new FinalVariableInstanceImportService(
      variableWriter,
      importAdapterProvider,
      elasticsearchImportJobExecutor,
      engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    IdSetBasedImportPage page = importIndexHandler.getNextPage();
    if (!page.getIds().isEmpty()) {
      List<HistoricVariableInstanceDto> entities = engineEntityFetcher.fetchHistoricVariableInstances(page);
      finalVariableInstanceImportService.executeImport(entities, page.getIds());
      return true;
    }
    return false;
  }

}
