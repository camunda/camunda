package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

public abstract class BackoffImportMediator<T extends ImportIndexHandler> implements EngineImportMediator {
  private Logger logger = LoggerFactory.getLogger(getClass());

  protected T importIndexHandler;

  @Autowired
  protected BeanHelper beanHelper;

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  @Autowired
  protected ImportIndexHandlerProvider provider;

  protected EngineContext engineContext;

  private BackoffCalculator backoffCalculator;

  public BackoffImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  private void initialize() {
    init();
    backoffCalculator = new BackoffCalculator(
            configurationService.getMaximumBackoff(),
            configurationService.getImportHandlerWait()
    );
  }

  protected abstract void init();

  protected abstract boolean importNextEnginePage();

  @Override
  public void importNextPage() {
    if (backoffCalculator.isReadyForNextRetry()) {
      boolean pageIsPresent = getNextPageWithErrorCheck();
      if (pageIsPresent) {
        backoffCalculator.resetBackoff();
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

  private void executeAfterMaxBackoffIsReached() {
    importIndexHandler.executeAfterMaxBackoffIsReached();
  }

  private void calculateNewDateUntilIsBlocked() {
    if (backoffCalculator.isMaximumBackoffReached()) {
      executeAfterMaxBackoffIsReached();
    }
    logDebugSleepInformation(backoffCalculator.calculateSleepTime());
  }

  private void logDebugSleepInformation(long sleepTime) {
    logger.debug(
      "Was not able to produce a new job, sleeping for [{}] ms",
      sleepTime
    );
  }

  @Override
  public boolean canImport() {
    boolean canImportNewPage = backoffCalculator.isReadyForNextRetry();
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
    return backoffCalculator.timeUntilNextRetryTime();
  }

  @Override
  public void resetBackoff() {
    backoffCalculator.resetBackoff();
  }

}
