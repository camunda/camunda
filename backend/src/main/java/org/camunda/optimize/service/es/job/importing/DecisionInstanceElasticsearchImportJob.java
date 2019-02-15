package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DecisionInstanceElasticsearchImportJob extends ElasticsearchImportJob<DecisionInstanceDto> {

  private DecisionInstanceWriter decisionInstanceWriter;

  public DecisionInstanceElasticsearchImportJob(DecisionInstanceWriter decisionInstanceWriter, Runnable callback) {
    super(callback);
    this.decisionInstanceWriter = decisionInstanceWriter;
  }

  @Override
  protected void persistEntities(List<DecisionInstanceDto> newOptimizeEntities) throws Exception {
    decisionInstanceWriter.importDecisionInstances(newOptimizeEntities);
  }
}
