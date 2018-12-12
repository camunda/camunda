package org.camunda.optimize.service.es.report.command.decision.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportNumberResultDto;
import org.camunda.optimize.service.es.report.command.decision.DecisionReportCommand;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;

public class CountDecisionFrequencyGroupByNoneCommand extends DecisionReportCommand<DecisionReportNumberResultDto> {

  @Override
  protected DecisionReportNumberResultDto evaluate() {

    final DecisionReportDataDto reportData = getDecisionReportData();
    logger.debug(
      "Evaluating count decision instance frequency grouped by none report " +
        "for decision definition key [{}] and version [{}]",
      reportData.getDecisionDefinitionKey(),
      reportData.getDecisionDefinitionVersion()
    );

    BoolQueryBuilder query = setupBaseQuery(
      reportData.getDecisionDefinitionKey(),
      reportData.getDecisionDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    SearchResponse response = esclient
      .prepareSearch(getOptimizeIndexAliasForType(DECISION_INSTANCE_TYPE))
      .setTypes(DECISION_INSTANCE_TYPE)
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .get();

    DecisionReportNumberResultDto numberResult = new DecisionReportNumberResultDto();
    numberResult.setResult(response.getHits().getTotalHits());
    numberResult.setDecisionInstanceCount(response.getHits().getTotalHits());
    return numberResult;
  }

}
