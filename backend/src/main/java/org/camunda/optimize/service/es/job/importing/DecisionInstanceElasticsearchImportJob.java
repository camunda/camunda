package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionInstanceElasticsearchImportJob extends ElasticsearchImportJob<DecisionInstanceDto> {
  private static final Logger logger = LoggerFactory.getLogger(DecisionInstanceElasticsearchImportJob.class);

  private DecisionInstanceWriter decisionInstanceWriter;

  public DecisionInstanceElasticsearchImportJob(DecisionInstanceWriter decisionInstanceWriter) {
    this.decisionInstanceWriter = decisionInstanceWriter;
  }

  @Override
  protected void executeImport() {
    try {
      decisionInstanceWriter.importProcessInstances(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing decision instances to elasticsearch", e);
    }
  }
}
