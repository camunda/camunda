package org.camunda.optimize.service.es.job;

import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.es.job.importing.RunningProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Represents one page of entities that should be added
 * to elasticsearch.
 */
public abstract class ElasticsearchImportJob<OPT extends OptimizeDto> implements Runnable {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final BackoffCalculator backoffCalculator = new BackoffCalculator(1L, 30L);
  private final Runnable callback;

  protected List<OPT> newOptimizeEntities = Collections.emptyList();

  protected ElasticsearchImportJob(Runnable callback) {
    this.callback = callback;
  }

  /**
   * Run the import job
   */
  @Override
  public void run() {
    executeImport();
  }

  /**
   * Prepares the given page of entities to be imported.
   *
   * @param pageOfOptimizeEntities that are not already in
   *                               elasticsearch and need to be imported.
   */
  public void setEntitiesToImport(List<OPT> pageOfOptimizeEntities) {
    this.newOptimizeEntities = pageOfOptimizeEntities;
  }

  protected void executeImport() {
    boolean success = false;
    while (!success) {
      try {
        persistEntities(newOptimizeEntities);
        success = true;
      } catch (Exception e) {
        logger.error("error while writing instances to elasticsearch", e);
        long sleepTime = backoffCalculator.calculateSleepTime();
        try {
          Thread.sleep(sleepTime);
        } catch (InterruptedException exception) {
          //
        }
      }
    }
    callback.run();
  }

  protected abstract void persistEntities(List<OPT> newOptimizeEntities) throws Exception;

}
