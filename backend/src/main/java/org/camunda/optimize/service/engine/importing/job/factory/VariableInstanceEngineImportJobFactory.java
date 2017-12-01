package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.VariableInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.impl.VariableInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.job.VariableInstanceEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.VariableWriter;
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
public class VariableInstanceEngineImportJobFactory implements EngineImportJobFactory {

  private VariableInstanceImportIndexHandler importIndexHandler;
  private MissingEntitiesFinder<HistoricVariableInstanceDto> missingEntitiesFinder;
  private VariableInstanceFetcher engineEntityFetcher;

  @Autowired
  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  @Autowired
  private BeanHelper beanHelper;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private Client esClient;

  @Autowired
  private VariableWriter variableWriter;

  @Autowired
  private ImportAdapterProvider importAdapterProvider;

  @Autowired
  private ImportIndexHandlerProvider provider;

  protected EngineContext engineContext;

  public VariableInstanceEngineImportJobFactory(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init(){
    importIndexHandler = provider.getVariableInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(VariableInstanceFetcher.class, engineContext);
    missingEntitiesFinder = new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getVariableType()
    );
  }

  @Override
  public long getBackoffTimeInMs() {
    return importIndexHandler.getBackoffTimeInMs();
  }

  public Optional<Runnable> getNextJob() {
    Optional<IdSetBasedImportPage> page = importIndexHandler.getNextPage();
    return page.map(
      idSetBasedImportPage -> new VariableInstanceEngineImportJob(
        variableWriter,
        importAdapterProvider,
        idSetBasedImportPage,
        elasticsearchImportJobExecutor,
        missingEntitiesFinder,
        engineEntityFetcher,
          engineContext
      )
    );
  }


}
