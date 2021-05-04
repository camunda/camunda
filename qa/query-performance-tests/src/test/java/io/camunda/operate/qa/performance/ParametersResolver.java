/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.qa.performance;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.apache.http.HttpHost;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.ListViewTemplate;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * This class helps to resolve placeholders in JSON query files. On startup it reads the required data from Elasticsearch.
 * This data is later used to replace placeholders.
 */
@Component
public class ParametersResolver {

  private static final String PROCESS_INSTANCE_IDS_PLACEHOLDER = "${processInstanceIds}";
  private static final String PROCESS_INSTANCE_ID_PLACEHOLDER = "${processInstanceId}";
  private static final String PROCESS_IDS_PLACEHOLDER = "${processIds}";
  private static final String PROCESS_ID_PLACEHOLDER = "${processId}";
  private static final String START_DATE_AFTER_PLACEHOLDER = "${startDateAfter}";
  private static final String START_DATE_BEFORE_PLACEHOLDER = "${startDateBefore}";

  private RestHighLevelClient esClient;

  @Autowired
  private DateTimeFormatter df;

  @Value("${camunda.operate.qa.queries.elasticsearch.host:localhost}")
  private String elasticsearchHost;

  @Value("${camunda.operate.qa.queries.elasticsearch.port:9200}")
  private Integer elasticsearchPort;

  @Value("${camunda.operate.qa.queries.elasticsearch.prefix:operate}")
  private String prefix;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ProcessIndex processIndex;

  @Autowired
  private ListViewTemplate listViewTemplate;

  private List<String> processInstanceIds = new ArrayList<>();
  private List<String> processIds = new ArrayList<>();
  private String startDateBefore;
  private String startDateAfter;

  private final ConstantScoreQueryBuilder isProcessInstanceQuery = constantScoreQuery(termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));

  private Random random = new Random();

  @PostConstruct
  public void resolveParameters() {
    esClient = new RestHighLevelClient(RestClient.builder(new HttpHost(elasticsearchHost, elasticsearchPort, "http")));
    initProcessInstanceIds();
    initProcessIds();
    initStartDates();
  }

  private void initStartDates() {
    try {
      final String listViewAlias = getAlias(listViewTemplate);
      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(isProcessInstanceQuery)
              .from(0).size(1)
          .sort(ListViewTemplate.START_DATE);
      SearchRequest searchRequest =
          new SearchRequest(listViewAlias).source(searchSourceBuilder);
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final SearchHits hits = response.getHits();
      if (hits.getHits().length == 0) {
        throw new RuntimeException("Error occurred when reading startDate from Elasticsearch: no records found");
      }
      final ProcessInstanceForListViewEntity wi = ElasticsearchUtil
          .fromSearchHit(hits.getHits()[0].getSourceAsString(), objectMapper, ProcessInstanceForListViewEntity.class);
      startDateAfter = wi.getStartDate().format(df);
      startDateBefore = wi.getStartDate().plus(1, ChronoUnit.MINUTES).format(df);
    } catch (IOException ex) {
      throw new RuntimeException("Error occurred when reading processInstanceIds from Elasticsearch", ex);
    }
  }

  private void initProcessInstanceIds() {
    try {
      final String listViewAlias = getAlias(listViewTemplate);
      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(isProcessInstanceQuery)
              .fetchSource(false)
              .from(0).size(50);
      SearchRequest searchRequest =
          new SearchRequest(listViewAlias).source(searchSourceBuilder);
      processInstanceIds = requestIdsFor(searchRequest);
    } catch (IOException ex) {
      throw new RuntimeException("Error occurred when reading processInstanceIds from Elasticsearch", ex);
    }
  }

  private void initProcessIds() {
    processIds = io.camunda.operate.qa.util.ElasticsearchUtil.getProcessIds(esClient, getAlias(processIndex), 2);
  }

  private List<String> requestIdsFor(SearchRequest searchRequest) throws IOException{
    final SearchHits hits = esClient.search(searchRequest, RequestOptions.DEFAULT).getHits();
    return Arrays.stream(hits.getHits()).collect(ArrayList::new, (list, hit) -> list.add(hit.getId()), (list1, list2) -> list1.addAll(list2));
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

  private boolean contains(String str, String substr) {
      return str!=null && str.contains(substr);
  }

  private String replacePlaceholdersInString(String string) throws IOException {
    if (contains(string,PROCESS_INSTANCE_IDS_PLACEHOLDER)) {
      string = replacePlaceholderWithIds(string, PROCESS_INSTANCE_IDS_PLACEHOLDER, processInstanceIds);
    }
    if (contains(string,PROCESS_INSTANCE_ID_PLACEHOLDER)) {
      string = replacePlaceholderWithRandomId(string, PROCESS_INSTANCE_ID_PLACEHOLDER, processInstanceIds);
    }
    if (contains(string,PROCESS_IDS_PLACEHOLDER)) {
      string = replacePlaceholderWithIds(string, PROCESS_IDS_PLACEHOLDER, processIds);
    }
    if (contains(string,PROCESS_ID_PLACEHOLDER)) {
      string = replacePlaceholderWithRandomId(string, PROCESS_ID_PLACEHOLDER, processIds);
    }
    if (contains(string,START_DATE_AFTER_PLACEHOLDER)) {
      string = replacePlaceholderWithString(string, START_DATE_AFTER_PLACEHOLDER, startDateAfter);
    }
    if (contains(string,START_DATE_BEFORE_PLACEHOLDER)) {
      string = replacePlaceholderWithString(string, START_DATE_BEFORE_PLACEHOLDER, startDateBefore);
    }
    return string;
  }

  private String replacePlaceholderWithIds(String body, String placeholder, List<String> ids) {
    final String idsString = ids.stream().collect(Collectors.joining("\",\""));
    body = body.replace(placeholder, idsString);
    return body;
  }

  private String replacePlaceholderWithRandomId(String body, String placeholder, List<String> ids) {
    final String id = ids.get(random.nextInt(ids.size()));
    return replacePlaceholderWithString(body, placeholder, id);
  }

  private String replacePlaceholderWithString(String body, String placeholder, String value) {
    body = body.replace(placeholder, value);
    return body;
  }

  private String getAlias(IndexDescriptor descriptor) {
    return String.format("%s-%s-%s_alias", prefix, descriptor.getIndexName(), descriptor.getVersion().toLowerCase());
  }

}
