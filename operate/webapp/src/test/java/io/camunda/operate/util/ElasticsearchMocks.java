/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import java.io.IOException;
import java.lang.reflect.Field;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentType;

public class ElasticsearchMocks {

  public static final int ELS_PORT = 9200;
  public static final String ELS_HOST = "localhost";
  public static final String ELS_SCHEME = "http";

  // create an ElasticSearch SearchResponse from a JSON response String
  public static SearchResponse getMockResponseFromJSON(final String jsonResponse)
      throws NoSuchFieldException, IllegalAccessException, IOException {
    final RestHighLevelClient client =
        new RestHighLevelClient(RestClient.builder(new HttpHost(ELS_HOST, ELS_PORT, ELS_SCHEME)));
    final Field registryField = client.getClass().getDeclaredField("registry");
    registryField.setAccessible(true);
    final NamedXContentRegistry registry = (NamedXContentRegistry) registryField.get(client);

    final XContentParser parser =
        XContentFactory.xContent(XContentType.JSON)
            .createParser(registry, LoggingDeprecationHandler.INSTANCE, jsonResponse);
    return SearchResponse.fromXContent(parser);
  }

  public static Terms getMockTermsFromJSONResponse(
      final String jsonResponse, final String termsAggregationName)
      throws IOException, NoSuchFieldException, IllegalAccessException {
    final SearchResponse searchResponse = getMockResponseFromJSON(jsonResponse);
    return searchResponse.getAggregations().get(termsAggregationName);
  }

  /*
   *  Create SearchResponse parts from templates
   */

  // get a batch operation elasticsearch SearchResponse with two instances and an aggregation as
  // JSON String
  public static String batchSearchResponseWithAggregationAsString(
      final int totalHitCount, final String hits, final String aggregation) {
    return String.format(
        "{\"took\":7,\"timed_out\":false,\"_shards\":{\"total\":2,\"successful\":2,\"skipped\":0,\"failed\":0},\"hits\":{\"total\":{\"value\":%s,\"relation\":\"eq\"},\"max_score\":1.0,%s},\"aggregations\":{%s}}",
        totalHitCount, hits, aggregation);
  }

  public static String emptyBatchSearchResponseWithAggregationAsString(final String aggregation) {
    return batchSearchResponseWithAggregationAsString(
        0, hitsFromTemplate(new String[] {}), aggregation);
  }

  public static String hitsFromTemplate(final String[] instances) {
    String hits = "";
    if (instances != null && instances.length > 0) {
      hits = String.join(",", instances);
    }
    return String.format("\"hits\":[%s]", hits);
  }

  public static String instanceFromTemplate(
      final String docId,
      final String sourceId,
      final String type,
      final String batchOperationId,
      final String username) {
    return String.format(
        "{\"_index\":\"operate-operation\",\"_type\":\"_doc\","
            + "\"_id\":\"%s\",\"_score\":1.0,\"_source\":{\"id\":\"%s\","
            + "\"processInstanceKey\":1111111111111111,\"processDefinitionKey\":2222222222222222,\"bpmnProcessId\":\"eventBasedGatewayProcess\","
            + "\"decisionDefinitionKey\":null,\"incidentKey\":null,\"scopeKey\":null,\"variableName\":null,\"variableValue\":null,"
            + "\"type\":\"%s\",\"lockExpirationTime\":null,\"lockOwner\":null,\"state\":\"COMPLETED\",\"errorMessage\":null,"
            + "\"batchOperationId\":\"%s\",\"zeebeCommandKey\":null,\"username\":\"%s\",\"modifyInstructions\":null,\"migrationPlan\":null}}",
        docId, sourceId, type, batchOperationId, username);
  }

  public static String termsAggregationFromTemplate(
      final String aggregationName, final String[] termsAggregationSubAggregations) {
    String subAggregations = "{}";
    if (termsAggregationSubAggregations != null && termsAggregationSubAggregations.length > 0) {
      subAggregations = String.join(",", termsAggregationSubAggregations);
    }
    return String.format(
        "\"sterms#%s\":{\"buckets\":[%s],\"doc_count_error_upper_bound\":0,\"sum_other_doc_count\":0}",
        aggregationName, subAggregations);
  }

  public static String termsAggregationEmptyFromTemplate(final String aggregationName) {
    return termsAggregationFromTemplate(aggregationName, null);
  }

  public static String termsSubaggregationFilterFromTemplate(
      final int docCount, final String filterAggregation, final String key) {
    return String.format("{\"doc_count\":%s,%s,\"key\":\"%s\"}", docCount, filterAggregation, key);
  }

  public static String twoBucketFilterAggregationFromTemplate(
      final String filterName,
      final String bucket1Name,
      final int bucket1DocCount,
      final String bucket2Name,
      final int bucket2DocCount) {
    return String.format(
        "\"filters#%s\":{\"buckets\":{\"%s\":{\"doc_count\":%s},\"%s\":{\"doc_count\":%s}}}",
        filterName, bucket1Name, bucket1DocCount, bucket2Name, bucket2DocCount);
  }
}
