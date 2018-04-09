package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher;
import org.camunda.optimize.service.engine.importing.index.page.ImportPage;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public abstract class ImportService<ENG extends EngineDto, OPT extends OptimizeDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected MissingEntitiesFinder<ENG> missingActivityFinder;
  protected EngineContext engineContext;

  public ImportService(
                       ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                       MissingEntitiesFinder<ENG> missingActivityFinder,
                       EngineContext engineContext
                         ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.missingActivityFinder = missingActivityFinder;
    this.engineContext = engineContext;
  }

  public void executeImport(List<ENG> pageOfEngineEntities) {
    logger.trace("Importing entities from engine...");

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


}
