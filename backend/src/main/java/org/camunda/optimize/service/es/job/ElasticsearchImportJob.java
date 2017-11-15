package org.camunda.optimize.service.es.job;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.util.Collections;
import java.util.List;

/**
 * Represents one page of entities that should be added
 * to elasticsearch.
 */
public abstract class ElasticsearchImportJob<OPT extends OptimizeDto> implements Runnable {

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
  protected abstract void executeImport();

}
