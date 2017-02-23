package org.camunda.optimize.service.importing.job;

import org.camunda.optimize.dto.optimize.OptimizeDto;

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

  protected List<OPT> newOptimizeEntities;

  /**
   * Run the import job by first fetching missing data
   * and then import the new entities to elasticsearch.
   */
  @Override
  public void run() {
    getAbsentAggregateInformation();
    executeImport();
  }

  public List<OPT> getNewOptimizeEntities() {
    return newOptimizeEntities;
  }

  /**
   * Prepares the given page of entities to be imported.
   *
   * @param pageOfOptimizeEntities that are not already in
   *                               elasticsearch and need to be imported.
   */
  public void addEntitiesToImport(List<OPT> pageOfOptimizeEntities) {
    this.newOptimizeEntities = pageOfOptimizeEntities;
  }

  /**
   * If information is still missing, this method enriches all
   * given entities from {@link #addEntitiesToImport(List)} with
   * the remaining information to fully represent the entity type.
   */
  protected  abstract void getAbsentAggregateInformation();

  /**
   * This executes the import and adds all the given entities
   * from {@link #addEntitiesToImport(List)} to elasticsearch.
   */
  protected  abstract void executeImport();

}
