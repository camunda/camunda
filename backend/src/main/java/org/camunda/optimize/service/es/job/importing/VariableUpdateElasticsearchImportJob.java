package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.variable.VariableUpdateWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VariableUpdateElasticsearchImportJob extends ElasticsearchImportJob<VariableDto> {

  private VariableUpdateWriter variableWriter;
  private Logger logger = LoggerFactory.getLogger(getClass());

  public VariableUpdateElasticsearchImportJob(VariableUpdateWriter variableWriter) {
    this.variableWriter = variableWriter;
  }

  @Override
  protected void executeImport() {
    try {
      variableWriter.importVariables(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing variable updates to Elasticsearch", e);
    }
  }

}
