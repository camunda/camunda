package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionXmlWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionDefinitionXmlElasticsearchImportJob extends ElasticsearchImportJob<DecisionDefinitionOptimizeDto> {
  private static final Logger logger = LoggerFactory.getLogger(DecisionDefinitionXmlElasticsearchImportJob.class);

  private DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;

  public DecisionDefinitionXmlElasticsearchImportJob(final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter) {
    this.decisionDefinitionXmlWriter = decisionDefinitionXmlWriter;
  }

  @Override
  protected void executeImport() {
    try {
      decisionDefinitionXmlWriter.importProcessDefinitionXmls(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing decision definitions to elasticsearch", e);
    }
  }
}
