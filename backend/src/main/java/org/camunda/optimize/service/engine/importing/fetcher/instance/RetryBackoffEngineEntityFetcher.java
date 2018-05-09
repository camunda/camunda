package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.EngineEntityFetcher;

import java.util.List;

public abstract class RetryBackoffEngineEntityFetcher<ENG extends EngineDto>
    extends EngineEntityFetcher {

  private static final long STARTING_BACKOFF = 0;
  private long backoffCounter = 0L;

  RetryBackoffEngineEntityFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  protected List<ENG> fetchWithRetry(FetcherFunction<ENG> fetchFunction) {
    List<ENG> result = null;
    while (result == null) {
      try {
        result = fetchFunction.fetch();
      } catch (Exception exception) {
        logError(exception);
        long timeToSleep = calculateSleepTime();
        logDebugSleepInformation(timeToSleep);
        sleep(timeToSleep);
      }
    }
    resetBackoffCounter();
    return result;
  }

  @FunctionalInterface
  public interface FetcherFunction<ENG> {
    List<ENG> fetch();
  }

  private void resetBackoffCounter() {
    backoffCounter = STARTING_BACKOFF;
  }

  private void sleep(long timeToSleep) {
    try {
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      logger.debug("Was interrupted from sleep. Continuing to fetch new entities.", e);
    }
  }

  private long calculateSleepTime() {
    backoffCounter = Math.min(backoffCounter + 1, configurationService.getMaximumBackoff());
    long interval = configurationService.getImportHandlerWait();
    long sleepTimeInMs = interval * backoffCounter;
    return sleepTimeInMs;
  }

  private void logDebugSleepInformation(long sleepTime) {
    logger.debug(
        "Sleeping for [{}] ms and retrying the fetching of the entities afterwards.",
        sleepTime
    );
  }

  private void logError(Exception e) {
    logger.error("Error during fetching of entities. Please check the connection with [{}]!", engineContext.getEngineAlias(), e);
  }

}
