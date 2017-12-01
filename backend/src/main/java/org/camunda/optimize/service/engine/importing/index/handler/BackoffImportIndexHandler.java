package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.service.engine.importing.index.page.ImportPage;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public abstract class BackoffImportIndexHandler<PAGE extends ImportPage, INDEX>
  implements ImportIndexHandler<PAGE, INDEX> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  private static final long STARTING_BACKOFF = 0;
  private long backoffCounter = 0L;
  private LocalDateTime dateUntilPaginationIsBlocked = LocalDateTime.MIN;

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected BeanHelper beanHelper;

  @PostConstruct
  private void initialize() {
    init();
  }

  protected abstract void init();

  @Override
  public Optional<PAGE> getNextPage() {
    if (isReadyToFetchNextPage()) {
      Optional<PAGE> page = getNextPageWithErrorCheck();
      if (page.isPresent()) {
        resetBackoff();
        return page;
      } else {
        calculateNewDateUntilIsBlocked();
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private Optional<PAGE> getNextPageWithErrorCheck() {
    try {
      return getNextImportPage();
    } catch (Exception e) {
      logger.error(
          "Was not able to produce next page. Maybe a problem with the connection to the engine [{}]?",
          this.getEngineContext(),
          e
      );
      return Optional.empty();
    }
  }

  /**
   * Retrieves all information to import a new page from the engine. With
   * especially an offset where to start the import and the number of
   * instances to fetch.
   */
  protected abstract Optional<PAGE> getNextImportPage();

  private void calculateNewDateUntilIsBlocked() {
    if (configurationService.isBackoffEnabled()) {
      backoffCounter = Math.min(backoffCounter + 1, configurationService.getMaximumBackoff());
      long interval = configurationService.getImportHandlerWait();
      long sleepTimeInMs = interval * backoffCounter;
      dateUntilPaginationIsBlocked = LocalDateTime.now().plus(sleepTimeInMs, ChronoUnit.MILLIS);
      logDebugSleepInformation(sleepTimeInMs);
    }
  }

  private void logDebugSleepInformation(long sleepTime) {
    logger.debug(
      "Was not able to produce a new job, sleeping for [{}] ms",
      sleepTime
    );
  }

  private boolean isReadyToFetchNextPage() {
    return dateUntilPaginationIsBlocked.isBefore(LocalDateTime.now());
  }

  public long getBackoffTimeInMs() {

    long backoffTime =  LocalDateTime.now().until(dateUntilPaginationIsBlocked, ChronoUnit.MILLIS);
    backoffTime = Math.max(0, backoffTime);
    return backoffTime;
  }

  public void resetBackoff() {
    this.backoffCounter = STARTING_BACKOFF;
  }

  @Override
  public void resetImportIndex() {
    resetBackoff();
  }
}
