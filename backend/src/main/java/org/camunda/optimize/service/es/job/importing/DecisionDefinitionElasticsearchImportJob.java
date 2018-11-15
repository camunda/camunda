package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionDefinitionElasticsearchImportJob extends ElasticsearchImportJob<DecisionDefinitionOptimizeDto> {
  private static final Logger logger = LoggerFactory.getLogger(DecisionDefinitionElasticsearchImportJob.class);

  private DecisionDefinitionWriter decisionDefinitionWriter;

  public DecisionDefinitionElasticsearchImportJob(DecisionDefinitionWriter decisionDefinitionWriter) {
    this.decisionDefinitionWriter = decisionDefinitionWriter;
  }

  @Override
  protected void executeImport() {
    try {
      decisionDefinitionWriter.importProcessDefinitions(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing process definitions to elasticsearch", e);
    }
  }
}
