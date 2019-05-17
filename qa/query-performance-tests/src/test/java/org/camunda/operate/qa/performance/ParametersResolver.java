/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.performance;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.apache.http.HttpHost;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This class helps to resolv placeholders in JSON query files. On startup it reads the required data from Elasticsearch.
 * This data is later used to replace placeholders.
 */
@Component
public class ParametersResolver {

  private static final String WORKFLOW_INSTANCE_IDS_PLACEHOLDER = "${workflowInstanceIds}";
  private static final String WORKFLOW_INSTANCE_ID_PLACEHOLDER = "${workflowInstanceId}";
  private static final String WORKFLOW_IDS_PLACEHOLDER = "${workflowIds}";
  private static final String WORKFLOW_ID_PLACEHOLDER = "${workflowId}";

  private RestHighLevelClient esClient;

  @Value("${camunda.operate.qa.queries.elasticsearch.host:localhost}")
  private String elasticsearchHost;

  @Value("${camunda.operate.qa.queries.elasticsearch.port:9200}")
  private Integer elasticsearchPort;

  @Value("${camunda.operate.qa.queries.elasticsearch.prefix:operate}")
  private String prefix;

  @Autowired
  private ObjectMapper objectMapper;

  private List<String> workflowInstanceIds;
  private List<String> workflowIds;

  private Random random = new Random();

  @PostConstruct
  public void resolveParameters() {
    esClient = new RestHighLevelClient(RestClient.builder(new HttpHost(elasticsearchHost, elasticsearchPort, "http")));
    initWorkflowInstanceIds();
    initWorkflowIds();
  }

  private void initWorkflowInstanceIds() {
    try {
      final String listViewAlias = getAlias(ListViewTemplate.INDEX_NAME);
      SearchRequest searchRequest =
          new SearchRequest(listViewAlias).source(new SearchSourceBuilder()
              .fetchSource(false)
              .from(0)
              .size(50));
      workflowInstanceIds = ElasticsearchUtil.scrollIdsToList(searchRequest, esClient);
    } catch (IOException ex) {
      throw new RuntimeException("Error occurred when reading workflowInstanceIds from Elasticsearch", ex);
    }
  }

  private void initWorkflowIds() {
    try {
      final String workflowAlias = getAlias(WorkflowIndex.INDEX_NAME);
      SearchRequest searchRequest =
          new SearchRequest(workflowAlias).source(new SearchSourceBuilder()
              .fetchSource(false)
              .from(0)
              .size(50));
      workflowIds = ElasticsearchUtil.scrollIdsToList(searchRequest, esClient);
    } catch (IOException ex) {
      throw new RuntimeException("Error occurred when reading workflowIds from Elasticsearch", ex);
    }
  }

  public void replacePlaceholdersInQuery(TestQuery testQuery) {
    try {
      //replace in url
      String url = testQuery.getUrl();
      url = replacePlaceholdersInString(url);
      testQuery.setUrl(url);
      //replace in pathParams
      String pathParams = testQuery.getPathParams();
      pathParams = replacePlaceholdersInString(pathParams);
      testQuery.setPathParams(pathParams);
      //replace in body
      String body = testQuery.getBody();
      body = replacePlaceholdersInString(body);
      if (body != testQuery.getBody()) {
        testQuery.setBody(objectMapper.readTree(body));
      }
    } catch (IOException e) {
      throw new RuntimeException("Error occurred when replacing placeholders in queries", e);
    }
  }

  private String replacePlaceholdersInString(String string) throws IOException {
    if (string != null && string.contains(WORKFLOW_INSTANCE_IDS_PLACEHOLDER)) {
      string = replacePlaceholderWithIds(string, WORKFLOW_INSTANCE_IDS_PLACEHOLDER, workflowInstanceIds);
    }
    if (string != null && string.contains(WORKFLOW_INSTANCE_ID_PLACEHOLDER)) {
      string = replacePlaceholderWithId(string, WORKFLOW_INSTANCE_ID_PLACEHOLDER, workflowInstanceIds);
    }
    if (string != null && string.contains(WORKFLOW_IDS_PLACEHOLDER)) {
      string = replacePlaceholderWithIds(string, WORKFLOW_IDS_PLACEHOLDER, workflowIds);
    }
    if (string != null && string.contains(WORKFLOW_ID_PLACEHOLDER)) {
      string = replacePlaceholderWithId(string, WORKFLOW_ID_PLACEHOLDER, workflowIds);
    }
    return string;
  }

  private String replacePlaceholderWithIds(String body, String placeholder, List<String> ids) {
    final String idsString = ids.stream().collect(Collectors.joining("\",\""));
    body = body.replace(placeholder, idsString);
    return body;
  }

  private String replacePlaceholderWithId(String body, String placeholder, List<String> ids) {
    final String id = ids.get(random.nextInt(ids.size()));
    body = body.replace(placeholder, id);
    return body;
  }

  private String getAlias(String indexName) {
    return String.format("%s-%s_alias", prefix, indexName);
  }

}
