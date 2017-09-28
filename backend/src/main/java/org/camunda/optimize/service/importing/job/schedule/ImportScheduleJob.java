package org.camunda.optimize.service.importing.job.schedule;

import java.time.LocalDateTime;

/**
 * Context of the import execution performed at the current moment of time.
 * Please note that neither this class nor it's inheritors should contain references
 * to complex services.
 *
 * Main intention of this class is to represent current state of the import.
 *
 * @author Askar Akhmerov
 */
public abstract class ImportScheduleJob {

  protected boolean pageBased = true;
  protected LocalDateTime dateUntilExecutionIsBlocked;
  protected String engineAlias;
  protected String elasticsearchType;

  public boolean isPageBased() {
    return pageBased;
  }

  public void setPageBased(boolean pageBased) {
    this.pageBased = pageBased;
  }

  public LocalDateTime getDateUntilExecutionIsBlocked() {
    return dateUntilExecutionIsBlocked;
  }

  public boolean isReadyToBeExecuted() {
    return dateUntilExecutionIsBlocked == null || LocalDateTime.now().isAfter(dateUntilExecutionIsBlocked);
  }

  public void makeImportScheduleJobExecutable() {
    dateUntilExecutionIsBlocked = null;
  }

  public void setDateUntilExecutionIsBlocked(LocalDateTime dateUntilExecutionIsBlocked) {
    this.dateUntilExecutionIsBlocked = dateUntilExecutionIsBlocked;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public void setEngineAlias(String engineAlias) {
    this.engineAlias = engineAlias;
  }

  public void setElasticsearchType(String elasticsearchType) {
    this.elasticsearchType = elasticsearchType;
  }

  public String getElasticsearchType() {
    return elasticsearchType;
  }
}
