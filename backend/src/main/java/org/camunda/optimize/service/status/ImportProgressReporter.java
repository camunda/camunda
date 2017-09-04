package org.camunda.optimize.service.status;

import org.camunda.optimize.dto.optimize.query.ConnectionStatusDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.provider.IndexHandlerProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ImportProgressReporter {

  @Autowired
  private ImportServiceProvider importServiceProvider;

  @Autowired
  private IndexHandlerProvider indexHandlerProvider;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private StatusCheckingService statusCheckingService;

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
    List<String> connectedEngineAliases = getAllConnectedEngines();
    if (connectedEngineAliases.isEmpty()) {
      throw new OptimizeException();
    }
    double totalEngineEntityCount = getTotalEngineEntityCount(connectedEngineAliases);
    double alreadyImportedCount = getAlreadyImportedCount(connectedEngineAliases);
    if (totalEngineEntityCount > 0) {
      int tempResult = (int) (Math.floor(alreadyImportedCount / totalEngineEntityCount * 100));
      return Math.min(tempResult, 100);
    } else {
      return 0;
    }
  }

  private double getTotalEngineEntityCount(List<String> connectedEngineAliases) throws OptimizeException {
    double totalEngineEntityCount = 0;
    if (configurationService.getConfiguredEngines() != null) {
      for (String engine : connectedEngineAliases) {
        totalEngineEntityCount = totalEngineEntityCount + getTotalEngineCount(engine);
      }
    }
    return totalEngineEntityCount;
  }

  private List<String> getAllConnectedEngines() {
    ConnectionStatusDto statusDto = statusCheckingService.getConnectionStatus();
    List<String> connectedEngines = new ArrayList<>();
    Map<String, Boolean> engineStatus = statusDto.getEngineConnections();
    for (Map.Entry<String, Boolean> statusEntry : engineStatus.entrySet()) {
      if (statusEntry.getValue()) {
        connectedEngines.add(statusEntry.getKey());
      }
    }
    return connectedEngines;
  }

  private double getAlreadyImportedCount(List<String> connectedEngineAliases) {
    double alreadyImportedCount = 0;
    for (ImportIndexHandler importIndexHandler : indexHandlerProvider.getAllHandlersForAliases(connectedEngineAliases)) {
      alreadyImportedCount += importIndexHandler.getAbsoluteImportIndex();
    }
    return alreadyImportedCount;
  }

  private double getTotalEngineCount(String engineAlias) throws OptimizeException {
    double engineCount = 0;
    for (PaginatedImportService importService : importServiceProvider.getPagedServices()) {
      ImportIndexHandler indexHandler = indexHandlerProvider.getIndexHandler(
          importService.getElasticsearchType(),
          importService.getIndexHandlerType(),
          engineAlias);

      engineCount += importService.getEngineEntityCount(indexHandler, engineAlias);
    }
    return engineCount;
  }
}
