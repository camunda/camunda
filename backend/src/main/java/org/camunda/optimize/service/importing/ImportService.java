package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;

/**
 * Every class that should import data from the
 * engine needs to implement this interface and
 * add themselves to the {@link ImportScheduler}.
 */
public interface ImportService {

  /**
   * examine engine data, perform diff to figure out which data has to be incrementally
   * added to Optimize and create aggregation jobs for missing entities
   *
   * @return number of pages with new data that has been processed
   */
  int executeImport() throws OptimizeException;

  /**
   * Showing the progress of the import and where it
   * starts to continue fetching engine entities.
   * @return the current start index from the import showing
   */
  int getImportStartIndex();

  /**
   * reset starting point of importing. Can be used to
   * set the import starting point to the beginning.
   * Thus, this will cause the import to fetch all
   * engine entries again.
   * Can be used to make sure that entities that
   * have been missed are imported as well.
   */
  void resetImportStartIndex();

  /**
   * This is used for doc id when persisting the import index.
   * @return returns the type where the data is stored in elasticsearch.
   */
  String getElasticsearchType();
}
