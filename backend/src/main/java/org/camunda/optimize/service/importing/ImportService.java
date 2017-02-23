package org.camunda.optimize.service.importing;

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
  int executeImport();

  /**
   * reset starting point of importing.
   */
  void resetImportStartIndex();
}
