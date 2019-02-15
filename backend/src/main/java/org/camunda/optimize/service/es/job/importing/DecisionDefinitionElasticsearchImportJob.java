package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DecisionDefinitionElasticsearchImportJob extends ElasticsearchImportJob<DecisionDefinitionOptimizeDto> {

  private DecisionDefinitionWriter decisionDefinitionWriter;

  public DecisionDefinitionElasticsearchImportJob(DecisionDefinitionWriter decisionDefinitionWriter) {
    super(() -> {});
    this.decisionDefinitionWriter = decisionDefinitionWriter;
  }

  @Override
  protected void persistEntities(List<DecisionDefinitionOptimizeDto> newOptimizeEntities) throws Exception {
    decisionDefinitionWriter.importProcessDefinitions(newOptimizeEntities);
  }
}
