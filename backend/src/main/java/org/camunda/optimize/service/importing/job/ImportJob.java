package org.camunda.optimize.service.importing.job;

import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.exceptions.OptimizeException;

import java.util.Collections;
import java.util.List;

/**
 * Represents one page of entities that should be added
 * to elasticsearch.
 */
public abstract class ImportJob<OPT extends OptimizeDto> implements Runnable {

  protected List<OPT> newOptimizeEntities = Collections.emptyList();

  /**
   * Run the import job
   */
  @Override
  public void run() {
    executeImport();
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
   * This executes the import and adds all the given entities
   * from {@link #setEntitiesToImport(List)} to elasticsearch.
   */
  protected  abstract void executeImport();

}
