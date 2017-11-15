package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.camunda.optimize.service.es.writer.VariableWriter;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VariableElasticsearchImportJob extends ElasticsearchImportJob<VariableDto> {

  private VariableWriter variableWriter;
  private Logger logger = LoggerFactory.getLogger(VariableElasticsearchImportJob.class);

  public VariableElasticsearchImportJob(VariableWriter variableWriter) {
    this.variableWriter = variableWriter;
  }

  @Override
  protected void executeImport() {
    try {
      variableWriter.importVariables(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing variables to elasticsearch", e);
    }
  }
}
