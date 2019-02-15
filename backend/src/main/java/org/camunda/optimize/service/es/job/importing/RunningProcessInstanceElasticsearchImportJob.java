package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RunningProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private RunningProcessInstanceWriter runningProcessInstanceWriter;

  public RunningProcessInstanceElasticsearchImportJob(RunningProcessInstanceWriter runningProcessInstanceWriter,
                                                      Runnable callback) {
    super(callback);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
  }
  protected void persistEntities(List<ProcessInstanceDto> newOptimizeEntities) throws Exception {
    runningProcessInstanceWriter.importProcessInstances(newOptimizeEntities);
  }
}
