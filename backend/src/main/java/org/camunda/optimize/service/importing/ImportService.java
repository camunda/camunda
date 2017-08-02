package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.job.schedule.ImportScheduleJob;

/**
 * Every class that should import data from the
 * engine needs to implement this interface and
 * add themselves to the {@link ImportScheduler}.
 */
public interface ImportService <JOB extends ImportScheduleJob> {

  /**
   * examine engine data, perform diff to figure out which data has to be incrementally
   * added to Optimize and create aggregation jobs for missing entities
   *
   * @return number of pages with new data that has been processed and additional ids based on
   * which further fetching can be done
   */
  ImportResult executeImport(JOB executionContext) throws OptimizeException;

  /**
   * This is used for doc id when persisting the import index.
   *
   * @return returns the type where the data is stored in elasticsearch.
   */
  String getElasticsearchType();
}
