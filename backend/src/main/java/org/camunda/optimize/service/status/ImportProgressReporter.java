package org.camunda.optimize.service.status;

import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
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

  /**
   * @return an integer representing the progress of the import. The number states a
   * percentage value in range [0, 100] rounded to next whole number.
   */
  public long computeTotalImportProgress() {
    double progress = computeProgress(indexHandlerProvider.getAllHandlers());
    return Math.round(progress);
  }

  public Map<String,Long> computeImportProgressPerEngine() {
    Map<String,Long> result = new HashMap<>();
    for (String engineAlias : configurationService.getConfiguredEngines().keySet()) {

      List<ImportIndexHandler> handlers = indexHandlerProvider.getAllHandlers(engineAlias);
      double progress = computeProgress(handlers);
      result.put(engineAlias, Math.round(progress));
    }
    return result;
  }

  private double computeProgress(List<ImportIndexHandler> allHandlers) {
    double totalProgress = 0;
    int handlersWithData = 0;
    for (ImportIndexHandler importIndexHandler : allHandlers) {
      OptionalDouble computedProgressOptional = importIndexHandler.computeProgress();
      Double computedProgress = computedProgressOptional.orElse(0.0);
      totalProgress += computedProgress;
      handlersWithData += computedProgressOptional.isPresent() ? 1 : 0;
      logDebugStatement(
          importIndexHandler.getClass().getSimpleName(),
          importIndexHandler.getEngineContext().getEngineAlias(),
          computedProgressOptional.isPresent(),
          computedProgress
      );
    }

    if (handlersWithData != 0) {
      totalProgress = totalProgress/handlersWithData;
    }
    totalProgress = Math.ceil(totalProgress);
    logger.debug("total progress [{}]%", totalProgress);
    return totalProgress;
  }

  private void logDebugStatement(String handlerName, String engineAlias, boolean isPresent, Double computedProgress) {
    logger.debug(
        "[{}] [{}] has data to import [{}] and thus the computed progress is [{}]",
        handlerName,
        engineAlias,
        isPresent,
        computedProgress
    );
  }

}
