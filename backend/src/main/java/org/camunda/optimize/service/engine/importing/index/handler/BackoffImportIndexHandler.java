package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.service.engine.importing.index.page.ImportPage;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public abstract class BackoffImportIndexHandler<PAGE extends ImportPage, INDEX>
  implements ImportIndexHandler<PAGE, INDEX> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  private static final long STARTING_BACKOFF = 0;
  private long backoffCounter = STARTING_BACKOFF;
  private OffsetDateTime dateUntilPaginationIsBlocked = OffsetDateTime.MIN;
  private OffsetDateTime nextReset;

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected BeanHelper beanHelper;

  @PostConstruct
  private void initialize() {
    init();
    setNextReset();
  }

  private void setNextReset() {
    ChronoUnit unit = ChronoUnit.MINUTES;
    try {
      unit = ChronoUnit.valueOf(configurationService.getImportResetIntervalUnit());
    } catch (Exception e) {
      //nothing to do falling back to default
    }
    nextReset = OffsetDateTime.now().plus(
        configurationService.getImportResetIntervalValue(),
        unit
    );
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


  public void restartImportCycle () {
    //nothing to do by default
  }

  private void calculateNewDateUntilIsBlocked() {
    if (configurationService.isBackoffEnabled()) {
      backoffCounter = Math.min(backoffCounter + 1, configurationService.getMaximumBackoff());
      if (backoffCounter == configurationService.getMaximumBackoff()) {
        restartOrReset();
      } else {
        long interval = configurationService.getImportHandlerWait();
        long sleepTimeInMs = interval * backoffCounter;
        dateUntilPaginationIsBlocked = OffsetDateTime.now().plus(sleepTimeInMs, ChronoUnit.MILLIS);
        logDebugSleepInformation(sleepTimeInMs);
      }
    }
  }

  private void restartOrReset() {
    try {
      if (OffsetDateTime.now().isAfter(this.nextReset)) {
        this.resetImportIndex();
        this.setNextReset();
      } else {
        this.resetBackoff();
        this.restartImportCycle();
      }
    } catch (Exception e) {
      logger.error("Can't restart or restart index for engine [{}]", this.getEngineContext().getEngineAlias());
    }
  }

  private void logDebugSleepInformation(long sleepTime) {
    logger.debug(
      "Was not able to produce a new job, sleeping for [{}] ms",
      sleepTime
    );
  }

  protected boolean isReadyToFetchNextPage() {
    boolean isReady = dateUntilPaginationIsBlocked.isBefore(OffsetDateTime.now());
    logger.debug("is ready to fetch next page [{}]", isReady);
    return isReady;
  }

  /**
   * Method is invoked by scheduler once no more jobs are created by factories
   * associated with import process from specific engine
   *
   * @return time to sleep for import process of an engine in general
   */
  public long getBackoffTimeInMs() {
    long backoffTime = configurationService.isBackoffEnabled() ? OffsetDateTime.now().until(dateUntilPaginationIsBlocked, ChronoUnit.MILLIS) : 0;
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
