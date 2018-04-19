package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.query.variable.ProcessInstanceId;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.VariableWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllVariablesImportedElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceId> {

  private VariableWriter variableWriter;
  private Logger logger = LoggerFactory.getLogger(AllVariablesImportedElasticsearchImportJob.class);

  public AllVariablesImportedElasticsearchImportJob(VariableWriter variableWriter) {
    this.variableWriter = variableWriter;
  }

  @Override
  protected void executeImport() {
    try {
      variableWriter.flagProcessInstanceWhereAllVariablesHaveBeenImported(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing variables to elasticsearch", e);
    }
  }
}
