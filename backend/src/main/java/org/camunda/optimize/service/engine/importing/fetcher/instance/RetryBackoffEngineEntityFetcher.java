package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.service.engine.importing.index.page.ImportPage;

import java.util.List;

public abstract class RetryBackoffEngineEntityFetcher<ENG extends EngineDto, PAGE extends ImportPage>
  extends EngineEntityFetcher<ENG, PAGE> {

  private static final long STARTING_BACKOFF = 0;
  private long backoffCounter = 0L;

  public RetryBackoffEngineEntityFetcher(String engineAlias) {
    super(engineAlias);
  }

  /**
   * Queries the engine to fetch the entities from there given a page,
   * which contains all the information of which chunk of data should be fetched.
   */
  protected abstract List<ENG> fetchEntities(PAGE page);

  @Override
  public List<ENG> fetchEngineEntities(PAGE page) {
    List<ENG> result = null;
    while (result == null) {
      try {
        result = fetchEntities(page);
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
    backoffCounter = Math.min(backoffCounter+1, configurationService.getMaximumBackoff());
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
    logger.error("Error during fetching of entities. Please check the connection!", e);
  }

}
