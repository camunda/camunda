package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CompletedProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private CompletedProcessInstanceWriter completedProcessInstanceWriter;

  public CompletedProcessInstanceElasticsearchImportJob(CompletedProcessInstanceWriter
                                                          completedProcessInstanceWriter, Runnable callback) {
    super(callback);
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
  }

  @Override
  protected void persistEntities(List<ProcessInstanceDto> newOptimizeEntities) throws Exception {
    completedProcessInstanceWriter.importProcessInstances(newOptimizeEntities);
  }
}
