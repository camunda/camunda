package org.camunda.optimize.service.status;

import org.camunda.optimize.dto.optimize.query.ConnectionStatusDto;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

@Component
public class ImportProgressReporter {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ImportIndexHandlerProvider indexHandlerProvider;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private StatusCheckingService statusCheckingService;

  public boolean allEntitiesAreImported() {
    try {
      return computeImportProgress() == 100L;
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
  public long computeImportProgress() throws OptimizeException {
    List<String> connectedEngineAliases = getAllConnectedEngines();
    if (connectedEngineAliases.isEmpty()) {
      throw new OptimizeException(
        "Unable to compute import progress. All configured engines cannot be reached! " +
          "Maybe there is a problem with the connection!"
      );
    }
    double progress = computeProgress(connectedEngineAliases);
    return Math.round(progress);
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

  private double computeProgress(List<String> connectedEngineAliases) {
    double totalProgress = 0;
    int handlersWithData = 0;
    for (ImportIndexHandler importIndexHandler : indexHandlerProvider.getAllHandlers()) {
      OptionalDouble computedProgressOptional = importIndexHandler.computeProgress();
      Double computedProgress = computedProgressOptional.orElse(0.0);
      totalProgress += computedProgress;
      handlersWithData += computedProgressOptional.isPresent()? 1 : 0;
      logDebugStatement(importIndexHandler.getClass().getSimpleName(),
        computedProgressOptional.isPresent(),
        computedProgress);
    }
    totalProgress = totalProgress/handlersWithData;
    totalProgress = Math.ceil(totalProgress);
    return totalProgress;
  }

  private void logDebugStatement(String handlerName, boolean isPresent, Double computedProgress) {
    logger.debug("[{}] has data to import [{}] and thus the computed progress is [{}]",
      handlerName,
      isPresent,
      computedProgress
      );
  }

}
