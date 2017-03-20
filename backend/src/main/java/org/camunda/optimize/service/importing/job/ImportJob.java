package org.camunda.optimize.service.importing.job;

import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.EngineEntityFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Represents one page of entities that should be added
 * to elasticsearch.
 *
 * If the optimize type is an aggregated entity meaning
 * information from the engine entities did not contain
 * all information needed, the missing information can
 * also be fetched.
 */
public abstract class ImportJob<OPT extends OptimizeDto> implements Runnable {

  private Logger logger = LoggerFactory.getLogger(this.getClass());
  protected List<OPT> newOptimizeEntities = Collections.emptyList();

  /**
   * Run the import job by first fetching missing data
   * and then import the new entities to elasticsearch.
   */
  @Override
  public void run() {
    try {
      getAbsentAggregateInformation();
      executeImport();
    } catch (OptimizeException e) {
      if (logger.isDebugEnabled()) {
        logger.error("error during import", e);
      } else {
        logger.warn(e.getMessage());
      }
    }

  }

  public List<OPT> getEntitiesToImport() {
    return newOptimizeEntities;
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

  /**
   * If information is still missing, this method enriches all
   * given entities from {@link #setEntitiesToImport(List)} with
   * the remaining information to fully represent the entity type.
   */
  protected  abstract void getAbsentAggregateInformation() throws OptimizeException;

  /**
   * This executes the import and adds all the given entities
   * from {@link #setEntitiesToImport(List)} to elasticsearch.
   */
  protected  abstract void executeImport();

}
