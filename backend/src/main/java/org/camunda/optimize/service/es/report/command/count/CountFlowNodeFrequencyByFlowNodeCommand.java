package org.camunda.optimize.service.es.report.command.count;

import org.camunda.optimize.dto.optimize.query.report.result.MapReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public class CountFlowNodeFrequencyByFlowNodeCommand extends ReportCommand {

  static final String MI_BODY = "multiInstanceBody";

  @Override
  protected ReportResultDto evaluate() throws IOException {

    logger.debug("Evaluating count flow node frequency grouped by flow node report " +
      "for process definition id [{}]", reportData.getProcessDefinitionId());

    BoolQueryBuilder query = setupBaseQuery(reportData.getProcessDefinitionId());
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    SearchResponse response = esclient
      .prepareSearch(configurationService.getOptimizeIndex())
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .addAggregation(createAggregation())
      .get();

    Map<String, Long> resultMap = processAggregations(response.getAggregations());
    MapReportResultDto resultDto =
      new MapReportResultDto();
    resultDto.setResult(resultMap);
    return resultDto;
  }

  private AggregationBuilder createAggregation() {
    return
      nested("events", "events")
        .subAggregation(
          filter(
            "filteredEvents",
            boolQuery()
              .mustNot(
                termQuery("events.activityType", MI_BODY)
              )
          )
          .subAggregation(AggregationBuilders
            .terms("activities")
            .size(Integer.MAX_VALUE)
            .field("events.activityId")
          )
        );
  }

  private Map<String, Long> processAggregations(Aggregations aggregations) {
    ValidationHelper.ensureNotNull("aggregations", aggregations);
    Nested activities = aggregations.get("events");
    Filter filteredActivities = activities.getAggregations().get("filteredEvents");
    Terms terms = filteredActivities.getAggregations().get("activities");
    Map<String, Long> result = new HashMap<>();
    for (Terms.Bucket b : terms.getBuckets()) {
      result.put(b.getKeyAsString(), b.getDocCount());
    }
    return result;
  }

  private BoolQueryBuilder setupBaseQuery(String processDefinitionId) {
    BoolQueryBuilder query;
    query = boolQuery()
      .must(termQuery("processDefinitionId", processDefinitionId));
    return query;
  }
}
