package org.camunda.optimize.service.engine.importing.job;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher;
import org.camunda.optimize.service.engine.importing.index.page.ImportPage;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public abstract class EngineImportJob<ENG extends EngineDto, OPT extends OptimizeDto, PAGE extends ImportPage> implements Runnable {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  private PAGE importPage;
  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private MissingEntitiesFinder<ENG> missingActivityFinder;
  protected EngineEntityFetcher<ENG, PAGE> engineEntityFetcher;

  public EngineImportJob(PAGE importPage,
                         ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                         MissingEntitiesFinder<ENG> missingActivityFinder,
                         EngineEntityFetcher<ENG, PAGE> engineEntityFetcher
                         ) {
    this.importPage = importPage;
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.missingActivityFinder = missingActivityFinder;
    this.engineEntityFetcher = engineEntityFetcher;
  }

  @Override
  public void run() {
    executeImport(importPage);
  }

  private void executeImport(PAGE engineImportPage) {
    logger.trace("Importing entities from engine...");

    List<ENG> pageOfEngineEntities = queryEngineRestPoint(engineImportPage);
    List<ENG> newEngineEntities =
          missingActivityFinder.retrieveMissingEntities(pageOfEngineEntities);
    boolean newDataIsAvailable = !newEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<OPT> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(newEngineEntities);
      ElasticsearchImportJob<OPT> elasticsearchImportJob = createElasticsearchImportJob(newOptimizeEntities);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    try {
      elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
    } catch (InterruptedException e) {
      logger.error("Was interrupted while trying to add new job to Elasticsearch import queue.", e);
    }
  }

  /**
   * Maps a list of engine entities to optimize entities.
   */
  protected List<OPT> mapEngineEntitiesToOptimizeEntities(List<ENG> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }


  /**
   * Given the optimize entities a new Elasticsearch job is created, which is then able
   * to import the given entities to Elasticsearch.
   */
  protected abstract ElasticsearchImportJob<OPT> createElasticsearchImportJob(List<OPT> newOptimizeEntities);


  /**
   * Maps a single engine entity to the associated optimize entity
   */
  protected abstract OPT mapEngineEntityToOptimizeEntity(ENG engineEntity);

  /**
   * Queries the engine to fetch the entities from there given a page,
   * which contains all the information of which chunk of data should be fetched.
   */
  protected List<ENG> queryEngineRestPoint(PAGE importPage) {
    return engineEntityFetcher.fetchEngineEntities(importPage);
  }

}
