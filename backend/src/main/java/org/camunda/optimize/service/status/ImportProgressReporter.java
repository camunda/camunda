package org.camunda.optimize.service.status;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.EngineEntityFetcher;
import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.importing.ImportServiceProvider;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ImportProgressReporter {

  @Autowired
  private EngineEntityFetcher engineEntityFetcher;

  @Autowired
  private ImportServiceProvider importServiceProvider;

  public boolean allEntitiesAreImported() {
    try {
      return computeImportProgress() == 100;
    } catch (OptimizeException e) {
      return false;
    }
  }

  /**
   * @return an integer representing the progress of the import. The number states a
   * percentage value in range [0, 100] rounded to next whole number.
   * @throws OptimizeException if there were problems while trying to fetch the historic activity instance count
   * or the process definition count from the engine.
   */
  public int computeImportProgress() throws OptimizeException {
    int totalEngineEntityCount =
      engineEntityFetcher.fetchHistoricActivityInstanceCount() +
        2 * engineEntityFetcher.fetchProcessDefinitionCount();
    double alreadyImportedCount = getAlreadyImportedCount();
    if (totalEngineEntityCount > 0) {
      int tempResult = (int) (Math.floor(alreadyImportedCount / totalEngineEntityCount * 100));
      return Math.min(tempResult, 100);
    } else {
      return 0;
    }
  }

  private double getAlreadyImportedCount() {
    double alreadyImportedCount = 0;
    for (PaginatedImportService importService : importServiceProvider.getPagedServices()) {
      alreadyImportedCount += importService.getImportStartIndex();
    }
    return alreadyImportedCount;
  }
}
