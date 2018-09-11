package org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withprocesspart;

import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.AbstractProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetricAggregationBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.scriptedMetric;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;


public abstract class AbstractProcessInstanceDurationGroupByNoneWithProcessPartCommand
    extends AbstractProcessInstanceDurationGroupByNoneCommand {

  protected static final String MI_BODY = "multiInstanceBody";
  protected static final String SCRIPT_AGGRATION = "scriptAggration";
  protected static final String NESTED_AGGREGATION = "nestedAggregation";
  protected static final String TERMS_AGGRATIONS = "termsAggrations";

  protected abstract long processAggregation(Aggregations aggregations);

  @Override
  protected BoolQueryBuilder setupBaseQuery(String processDefinitionKey, String processDefinitionVersion) {
    BoolQueryBuilder boolQueryBuilder = super.setupBaseQuery(processDefinitionKey, processDefinitionVersion);
    String termPath = ProcessInstanceType.EVENTS + "." + ProcessInstanceType.ACTIVITY_ID;
    boolQueryBuilder.must(nestedQuery(
      ProcessInstanceType.EVENTS,
        termQuery(termPath, reportData.getProcessPart().getStart()),
      ScoreMode.None)
    );
    boolQueryBuilder.must(nestedQuery(
      "events",
        termQuery(termPath, reportData.getProcessPart().getEnd()),
      ScoreMode.None)
    );
    return boolQueryBuilder;
  }

  @Override
  protected AggregationBuilder createAggregationOperation(String fieldName) {
    Map<String, Object> params = new HashMap<>();
    params.put("_agg", new HashMap<>());
    params.put("startFlowNodeId", reportData.getProcessPart().getStart());
    params.put("endFlowNodeId", reportData.getProcessPart().getEnd());

    ScriptedMetricAggregationBuilder findStartAndEndDatesForEvents = scriptedMetric(SCRIPT_AGGRATION)
      .initScript(createInitScript())
      .mapScript(createMapScript())
      .combineScript(createCombineScript())
      .reduceScript(getReduceScript())
      .params(params);
    NestedAggregationBuilder searchThroughTheEvents =
      nested(NESTED_AGGREGATION, ProcessInstanceType.EVENTS);
    return
      terms(TERMS_AGGRATIONS)
      .field(ProcessInstanceType.PROCESS_INSTANCE_ID)
      .subAggregation(
        searchThroughTheEvents
          .subAggregation(
            findStartAndEndDatesForEvents
        )
      );
  }

  private Script createInitScript() {
    return new Script("params._agg.starts = []; params._agg.ends = []");
  }

  private Script createMapScript() {
    return new Script(
      "if(doc['events.activityId'].value == params.startFlowNodeId && doc['events.startDate'].value != null) {" +
        "long startDateInMillis = doc['events.startDate'].value.getMillis();" +
        "params._agg.starts.add(startDateInMillis);" +
      "} else if(doc['events.activityId'].value == params.endFlowNodeId && doc['events.endDate'].value != null) {" +
        "long endDateInMillis = doc['events.endDate'].value.getMillis();" +
        "params._agg.ends.add(endDateInMillis);" +
      "}"
    );
  }

  private Script createCombineScript() {
    return new Script(
        "long minStart = params._agg.starts.stream().min(Long::compareTo).get(); " +
          "long closestEnd = params._agg.ends.stream()" +
            ".min(Comparator.comparingDouble(v -> Math.abs(v - minStart))).get();" +
          "return closestEnd-minStart;"
    );
  }

  private Script getReduceScript() {
    return new Script("long sum = 0; for (a in params._aggs) { sum += a } return sum");
  }

}
