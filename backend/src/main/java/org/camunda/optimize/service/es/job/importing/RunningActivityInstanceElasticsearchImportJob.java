package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningActivityInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RunningActivityInstanceElasticsearchImportJob extends ElasticsearchImportJob<FlowNodeEventDto> {

  private RunningActivityInstanceWriter runningActivityInstanceWriter;

  public RunningActivityInstanceElasticsearchImportJob(RunningActivityInstanceWriter runningActivityInstanceWriter, Runnable callback) {

    super(callback);
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
  }

  @Override
  protected void persistEntities(List<FlowNodeEventDto> newOptimizeEntities) throws Exception {
    runningActivityInstanceWriter.importActivityInstances(newOptimizeEntities);
  }
}
