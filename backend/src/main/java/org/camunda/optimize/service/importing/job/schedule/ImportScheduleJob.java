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
  protected LocalDateTime timeToExecute;
  protected String engineAlias;
  protected String elasticsearchType;

  public boolean isPageBased() {
    return pageBased;
  }

  public void setPageBased(boolean pageBased) {
    this.pageBased = pageBased;
  }

  public LocalDateTime getTimeToExecute() {
    return timeToExecute;
  }

  public void setTimeToExecute(LocalDateTime timeToExecute) {
    this.timeToExecute = timeToExecute;
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
