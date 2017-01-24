package org.camunda.optimize.service.es;

import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.scripted.InternalScriptedMetric;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * @author Askar Akhmerov
 */
@Component
public class HeatMapReader {
  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;

  public TransportClient getEsclient() {
    return esclient;
  }

  public void setEsclient(TransportClient esclient) {
    this.esclient = esclient;
  }

  public Map <String, Long> getHeatMap(String processDefinitionKey) {
    Map <String, Long> result = new HashMap<>();

    QueryBuilder query;
    SearchRequestBuilder srb = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes("event");
    if (processDefinitionKey != null) {
      query = QueryBuilders.matchQuery("processDefinitionKey", processDefinitionKey);
      srb.setQuery(query);
    }

    SearchResponse sr = srb
        .addAggregation(AggregationBuilders
            .terms("activities")
            .field("activityId")
        )
        .execute().actionGet();

    Terms activities = sr.getAggregations().get("activities");
    for (Terms.Bucket b : activities.getBuckets()) {
      result.put(b.getKeyAsString(), b.getDocCount());
    }
    return result;
  }

  public Long activityCorrelation(String processDefinitionKey, List<String> activities) {
    Long result = null;

    QueryBuilder query;
    SearchRequestBuilder srb = esclient.prepareSearch();
    if (processDefinitionKey != null) {
      query = QueryBuilders.matchQuery("processDefinitionKey", processDefinitionKey);
      srb.setQuery(query);
    }

    Map<String,Object> parameters = new HashMap<String,Object>();
    parameters.put("_targetActivities", activities);
    parameters.put("_agg", new HashMap<>());


    SearchResponse sr = srb
        .addAggregation(AggregationBuilders
            .scriptedMetric("processesWithActivities")
            .initScript(getInitScript())
            .mapScript(getMapScript())
            .reduceScript(getReduceScript())
            .params(parameters)
        )
        .execute().actionGet();

    InternalScriptedMetric processesWithActivities = sr.getAggregations().get("processesWithActivities");
    if (processesWithActivities.aggregation() != null) {
      result = Long.valueOf(processesWithActivities.aggregation().toString());
    }

    return result;
  }

  private Script getReduceScript() {
    return new Script(
        ScriptType.INLINE,
        "groovy",
        getContent(configurationService.getCorrelationReduceScriptPath()),
        new HashMap<>()
    );
  }

  private Script getMapScript() {
    return new Script(
        ScriptType.INLINE,
        "groovy",
        getContent(configurationService.getCorrelationMapScriptPath()),
        new HashMap<>()
    );
  }

  private Script getInitScript() {
    return new Script(
        ScriptType.INLINE,
        "painless",
        getContent(configurationService.getCorrelationInitScriptPath()),
        new HashMap<>()
        );
  }

  private String getContent(String script) {
    InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(script);
    Scanner s = new Scanner(inputStream).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }
}
