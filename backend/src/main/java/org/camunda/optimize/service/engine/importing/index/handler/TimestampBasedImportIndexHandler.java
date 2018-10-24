package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.camunda.optimize.service.es.reader.TimestampBasedImportIndexReader;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public abstract class TimestampBasedImportIndexHandler
  implements ImportIndexHandler<TimestampBasedImportPage, TimestampBasedImportIndexDto> {
  private static final int CURRENT_TIME_BACKOFF_MILLIS = 2000;

  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private TimestampBasedImportIndexReader importIndexReader;
  @Autowired
  protected ConfigurationService configurationService;
  @Autowired
  protected BeanHelper beanHelper;

  private OffsetDateTime timestampOfLastEntity = getTimestampBeforeEngineExisted();

  protected EngineContext engineContext;

  public TimestampBasedImportIndexHandler(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  protected void init() {
    readIndexFromElasticsearch();
  }

  /**
   * States the Elasticsearch document name where the index information should be stored.
   */
  protected abstract String getElasticsearchDocID();

  public void updateTimestampOfLastEntity(final OffsetDateTime timestamp) {
    final OffsetDateTime backOffWindowStart = reduceByCurrentTimeBackoff(
      OffsetDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
    );
    if (timestamp.isAfter(backOffWindowStart)) {
      logger.info(
        "Timestamp is in the current time backoff window of {}ms, will save begin of backoff window as last timestamp",
        CURRENT_TIME_BACKOFF_MILLIS
      );
      this.timestampOfLastEntity= backOffWindowStart;
    } else {
      this.timestampOfLastEntity = timestamp;
    }
  }

  public OffsetDateTime getTimestampOfLastEntity() {
    return timestampOfLastEntity;
  }

  @Override
  public void readIndexFromElasticsearch() {
    Optional<TimestampBasedImportIndexDto> dto =
      importIndexReader.getImportIndex(getElasticsearchDocID(), engineContext.getEngineAlias());
    if (dto.isPresent()) {
      TimestampBasedImportIndexDto loadedImportIndex = dto.get();
      timestampOfLastEntity = loadedImportIndex.getTimestampOfLastEntity();
    }
  }

  @Override
  public TimestampBasedImportPage getNextPage() {
    TimestampBasedImportPage page = new TimestampBasedImportPage();
    page.setTimestampOfLastEntity(timestampOfLastEntity);
    return page;
  }

  @Override
  public TimestampBasedImportIndexDto createIndexInformationForStoring() {
    TimestampBasedImportIndexDto indexToStore = new TimestampBasedImportIndexDto();
    indexToStore.setTimestampOfLastEntity(timestampOfLastEntity);
    indexToStore.setEngine(this.engineContext.getEngineAlias());
    indexToStore.setEsTypeIndexRefersTo(getElasticsearchDocID());
    return indexToStore;
  }

  @Override
  public void resetImportIndex() {
    timestampOfLastEntity = getTimestampBeforeEngineExisted();
  }

  /**
   * Resets the process definitions to import, but keeps the last timestamps
   * for every respective process definition. Thus, we are not importing
   * all the once again, but starting from the last point we stopped at.
   */
  public void executeAfterMaxBackoffIsReached() {
    logger.debug("Restarting import cycle for document id [{}]", getElasticsearchDocID());
  }

  public void updateImportIndex() {
    executeAfterMaxBackoffIsReached();
  }

  @Override
  public EngineContext getEngineContext() {
    return engineContext;
  }

  private OffsetDateTime getTimestampBeforeEngineExisted() {
    return OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  }

  private OffsetDateTime reduceByCurrentTimeBackoff(OffsetDateTime currentDateTime) {
    return currentDateTime.minus(CURRENT_TIME_BACKOFF_MILLIS, ChronoUnit.MILLIS);
  }

}
