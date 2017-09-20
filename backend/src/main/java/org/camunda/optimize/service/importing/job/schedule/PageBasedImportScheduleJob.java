package org.camunda.optimize.service.importing.job.schedule;


import org.camunda.optimize.service.exceptions.BackoffException;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;

import java.time.LocalDateTime;

/**
 * @author Askar Akhmerov
 */
public class PageBasedImportScheduleJob extends ImportScheduleJob {

  private int absoluteImportIndex;
  private int relativeImportIndex;
  private Integer currentDefinitionBasedImportIndex;
  private String currentProcessDefinitionId;

  public PageBasedImportScheduleJob(
      int absoluteImportIndex,
      int relativeImportIndex,
      Integer currentDefinitionBasedImportIndex,
      String currentProcessDefinitionId
  ) {
    this.absoluteImportIndex = absoluteImportIndex;
    this.relativeImportIndex = relativeImportIndex;
    this.currentDefinitionBasedImportIndex = currentDefinitionBasedImportIndex;
    this.currentProcessDefinitionId = currentProcessDefinitionId;
  }

  public PageBasedImportScheduleJob(int absoluteImportIndex, int relativeImportIndex) {
    this(absoluteImportIndex,relativeImportIndex,null,null);
  }

  public int getAbsoluteImportIndex() {
    return absoluteImportIndex;
  }

  public int getCurrentDefinitionBasedImportIndex() {
    return currentDefinitionBasedImportIndex;
  }

  public String getCurrentProcessDefinitionId() {
    return currentProcessDefinitionId;
  }

  public int getRelativeImportIndex() {
    return relativeImportIndex;
  }
}
