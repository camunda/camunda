package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public abstract class BackoffImportMediator<T extends ImportIndexHandler> implements EngineImportMediator {

  protected T importIndexHandler;
  @Autowired
  protected BeanHelper beanHelper;

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected Client esClient;

  @Autowired
  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  @Autowired
  protected ImportIndexHandlerProvider provider;

  protected EngineContext engineContext;


  protected Logger logger = LoggerFactory.getLogger(getClass());

  private static final long STARTING_BACKOFF = 0;
  private long backoffCounter = STARTING_BACKOFF;
  private OffsetDateTime dateUntilPaginationIsBlocked = OffsetDateTime.now().minusMinutes(1L);
  private OffsetDateTime nextReset;

  @PostConstruct
  private void initialize() {
    init();
    setNextReset();
  }

  private void setNextReset() {
    ChronoUnit unit = ChronoUnit.HOURS;
    try {
      unit = unitOf(configurationService.getImportResetIntervalUnit());
    } catch (Exception e) {
      //nothing to do falling back to default
      logger.error("Was not able to parse interval unit [{}] for import reset. Using hours instead!",
        configurationService.getImportResetIntervalUnit());
    }
    nextReset = OffsetDateTime.now().plus(
        configurationService.getImportResetIntervalValue(),
        unit
    );
  }

  private ChronoUnit unitOf(String unit) {
    return ChronoUnit.valueOf(unit.toUpperCase());
  }

  protected abstract void init();

  protected abstract boolean importNextEnginePage();

  @Override
  public void importNextPage() {
    if (isReadyToFetchNextPage()) {
      boolean pageIsPresent = getNextPageWithErrorCheck();
      if (pageIsPresent) {
        resetBackoff();
      } else {
        calculateNewDateUntilIsBlocked();
      }
    }
  }

  private boolean getNextPageWithErrorCheck() {
    try {
      return importNextEnginePage();
    } catch (Exception e) {
      logger.error(
          "Was not able to produce next page. Maybe a problem with the connection to the engine [{}]?",
          this.engineContext.getEngineAlias(),
          e
      );
      return false;
    }
  }

  public void restartImportCycle () {
    importIndexHandler.restartImportCycle();
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
      logger.error("Can't restart or restart index for engine [{}]", this.engineContext.getEngineAlias());
    }
  }

  private void logDebugSleepInformation(long sleepTime) {
    logger.debug(
      "Was not able to produce a new job, sleeping for [{}] ms",
      sleepTime
    );
  }

  private boolean isReadyToFetchNextPage() {
    return dateUntilPaginationIsBlocked.isBefore(OffsetDateTime.now());
  }

  @Override
  public boolean canImport() {
    boolean canImportNewPage = isReadyToFetchNextPage() ;
    logger.debug("can import next page [{}]", canImportNewPage);
    return canImportNewPage;
  }

  /**
   * Method is invoked by scheduler once no more jobs are created by factories
   * associated with import process from specific engine
   *
   * @return time to sleep for import process of an engine in general
   */
  public long getBackoffTimeInMs() {
    long backoffTime = configurationService.isBackoffEnabled() ? OffsetDateTime.now().until(dateUntilPaginationIsBlocked, ChronoUnit.MILLIS) : 0L;
    backoffTime = Math.max(0, backoffTime);
    return backoffTime;
  }

  @Override
  public void resetBackoff() {
    this.backoffCounter = STARTING_BACKOFF;
    dateUntilPaginationIsBlocked = OffsetDateTime.now().minusMinutes(1L);
  }

  public void resetImportIndex() {
    resetBackoff();
    importIndexHandler.resetImportIndex();
  }
}
