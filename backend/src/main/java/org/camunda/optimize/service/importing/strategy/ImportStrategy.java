package org.camunda.optimize.service.importing.strategy;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeException;

import java.util.List;

public interface ImportStrategy {

  /**
   * Initializes the import index.
   *
   * @param elasticsearchType to which the import index is refering to.
   * @param maxPageSize       the maximum page size every page should contain.
   */
  void initializeImportIndex(String elasticsearchType, int maxPageSize);

  /**
   * If there are no new engine entities found the import index
   * can adjust its index accordingly. E.g., the process definition
   * based strategy would switch to the next process definition.
   */
  boolean adjustIndexWhenNoResultsFound(boolean hasNewData);


  /**
   * Write all information of the current import index to elasticsearch.
   * If optimized is restarted the import index can thus be restored again.
   */
  void persistImportIndexToElasticsearch();

  /**
   * Moves the import index for the given number of units.
   */
  void moveImportIndex(int units);

  /**
   * If the import is not based on the total quantity, but e.g. a process
   * definition, this method returns the relative import index (e.g. related to that
   * process definition).
   */
  int getRelativeImportIndex();

  // for progress measurement

  /**
   * Independent of what the import strategy is, this method returns
   * the total number of entities that were already imported in the
   * import service. This is especially useful for the
   * {@link org.camunda.optimize.service.status.ImportProgressReporter}
   */
  int getAbsoluteImportIndex();

  /**
   * Resets the import index such that it can start the import
   * all over again. E.g., that can be helpful to import
   * entities that were missed during the first round.
   */
  void resetImportIndex();
}
