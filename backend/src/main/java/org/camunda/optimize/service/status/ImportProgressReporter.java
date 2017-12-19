package org.camunda.optimize.service.status;

import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.OptionalDouble;

@Component
public class ImportProgressReporter {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ImportIndexHandlerProvider indexHandlerProvider;

  /**
   * @return an integer representing the progress of the import. The number states a
   * percentage value in range [0, 100] rounded to next whole number.
   * @throws OptimizeException if there were problems while trying to fetch the historic activity instance count
   * or the process definition count from the engine.
   */
  public long computeImportProgress() {
    double progress = computeProgress();
    return Math.round(progress);
  }

  private double computeProgress() {
    double totalProgress = 0;
    int handlersWithData = 0;
    for (ImportIndexHandler importIndexHandler : indexHandlerProvider.getAllHandlers()) {
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
    totalProgress = totalProgress/handlersWithData;
    totalProgress = Math.ceil(totalProgress);
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
