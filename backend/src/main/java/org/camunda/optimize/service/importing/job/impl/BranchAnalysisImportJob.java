package org.camunda.optimize.service.importing.job.impl;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.BranchAnalysisDataWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BranchAnalysisImportJob extends ImportJob<EventDto> {

    private Logger logger = LoggerFactory.getLogger(BranchAnalysisImportJob.class);
  private BranchAnalysisDataWriter branchAnalysisDataWriter;

  public BranchAnalysisImportJob(BranchAnalysisDataWriter branchAnalysisDataWriter) {
    this.branchAnalysisDataWriter = branchAnalysisDataWriter;
  }

  @Override
  protected void getAbsentAggregateInformation() throws OptimizeException {
    // nothing to do here
  }

  @Override
  protected void executeImport() {
    try {
      branchAnalysisDataWriter.importEvents(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing branch analysis data to elasticsearch", e);
    }
  }
}
