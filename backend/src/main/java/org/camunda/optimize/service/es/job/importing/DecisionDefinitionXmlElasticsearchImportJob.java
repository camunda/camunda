package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionXmlWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DecisionDefinitionXmlElasticsearchImportJob extends ElasticsearchImportJob<DecisionDefinitionOptimizeDto> {

  private DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;

  public DecisionDefinitionXmlElasticsearchImportJob(final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter) {
    super(() -> {});
    this.decisionDefinitionXmlWriter = decisionDefinitionXmlWriter;
  }

  @Override
  protected void persistEntities(List<DecisionDefinitionOptimizeDto> newOptimizeEntities) throws Exception {
    decisionDefinitionXmlWriter.importProcessDefinitionXmls(newOptimizeEntities);
  }
}
