package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.ImportPage;

import java.util.Optional;
import java.util.OptionalDouble;

public interface ImportIndexHandler<PAGE extends ImportPage, INDEX> {

  /**
   * Retrieves all information to import a new page from the engine. With
   * especially an offset where to start the import and the number of
   * instances to fetch.
   */
  Optional<PAGE> getNextPage();

  /**
   * Computes how far the import already progressed. Returns a number between
   * 0 (nothing imported yet) and 100 (every was imported).
   */
  OptionalDouble computeProgress();

  /**
   * Creates a data transfer object (DTO) about an index to store that
   * to Elasticsearch. On every restart of Optimize this information
   * can be used to continue the import where it stopped the last time.
   */
  INDEX createIndexInformationForStoring();

  /**
   * Initializes the import index.
   */
  void readIndexFromElasticsearch();


  /**
   * @return true if the import index handler is able to create another page and
   * false if there is no new data or the handler is doing backoff.
   */
  boolean hasNewPage();

  /**
   * Resets the import index such that it can start the import
   * all over again. E.g., that can be helpful to import
   * entities that were missed during the first round.
   */
  void resetImportIndex();

  void restartImportCycle();

  EngineContext getEngineContext();
}
