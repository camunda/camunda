/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.performance;

import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * This class helps to resolve placeholders in JSON query files. On startup it reads the required
 * data from Elasticsearch. This data is later used to replace placeholders.
 */
@Component
public class ParametersResolver {

  private static final String PROCESS_INSTANCE_IDS_MANY_VARS_PLACEHOLDER =
      "${processInstanceIdWithLotsOfVariables}";
  private static final String PROCESS_INSTANCE_IDS_PLACEHOLDER = "${processInstanceIds}";
  private static final String PROCESS_INSTANCE_ID_PLACEHOLDER = "${processInstanceId}";
  private static final String VARIABLE_ID_PLACEHOLDER = "${variableId}";
  private static final String PROCESS_IDS_PLACEHOLDER = "${processIds}";
  private static final String PROCESS_ID_PLACEHOLDER = "${processId}";
  private static final String START_DATE_AFTER_PLACEHOLDER = "${startDateAfter}";
  private static final String START_DATE_BEFORE_PLACEHOLDER = "${startDateBefore}";
  private final ConstantScoreQueryBuilder isProcessInstanceQuery =
      constantScoreQuery(termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
  private RestHighLevelClient esClient;
  @Autowired private DateTimeFormatter df;

  @Value("${camunda.operate.qa.queries.elasticsearch.url:http://localhost:9200}")
  private String elasticsearchUrl;

  @Value("${camunda.operate.qa.queries.elasticsearch.prefix:operate}")
  private String prefix;

  @Autowired private ObjectMapper objectMapper;

  @Autowired
  @Qualifier("operateProcessIndex")
  private ProcessIndex processIndex;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired
  @Qualifier("operateVariableTemplate")
  private VariableTemplate variableTemplate;

  private List<String> processInstanceIds = new ArrayList<>();
  private String processInstanceIdWithLotsOfVariables;
  private List<String> processIds = new ArrayList<>();
  private String startDateBefore;
  private String startDateAfter;
  private final Random random = new Random();

  @PostConstruct
  public void resolveParameters() throws URISyntaxException {
    final URI uri = new URI(elasticsearchUrl);
    esClient =
        new RestHighLevelClient(
            RestClient.builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme())));
    initProcessInstanceIds();
    findProcessInstanceIdWithManyVars();
    initProcessIds();
    initStartDates();
  }

  private void initStartDates() {
    try {
      final String listViewAlias = getAlias(listViewTemplate);
      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(isProcessInstanceQuery)
              .from(0)
              .size(1)
              .sort(ListViewTemplate.START_DATE);
      final SearchRequest searchRequest =
          new SearchRequest(listViewAlias).source(searchSourceBuilder);
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final SearchHits hits = response.getHits();
      if (hits.getHits().length == 0) {
        throw new RuntimeException(
            "Error occurred when reading startDate from Elasticsearch: no records found");
      }
      final ProcessInstanceForListViewEntity pi =
          ElasticsearchUtil.fromSearchHit(
              hits.getHits()[0].getSourceAsString(),
              objectMapper,
              ProcessInstanceForListViewEntity.class);
      startDateAfter = pi.getStartDate().format(df);
      startDateBefore = pi.getStartDate().plus(1, ChronoUnit.MINUTES).format(df);
    } catch (final IOException ex) {
      throw new RuntimeException(
          "Error occurred when reading processInstanceIds from Elasticsearch", ex);
    }
  }

  private void initProcessInstanceIds() {
    try {
      final String listViewAlias = getAlias(listViewTemplate);
      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(isProcessInstanceQuery)
              .fetchSource(false)
              .from(0)
              .size(50);
      final SearchRequest searchRequest =
          new SearchRequest(listViewAlias).source(searchSourceBuilder);
      processInstanceIds = requestIdsFor(searchRequest);
    } catch (final IOException ex) {
      throw new RuntimeException(
          "Error occurred when reading processInstanceIds from Elasticsearch", ex);
    }
  }

  private void findProcessInstanceIdWithManyVars() {
    try {
      final String variableAlias = getAlias(variableTemplate);
      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(termQuery(VariableTemplate.NAME, "many_vars_0"))
              .fetchSource(VariableTemplate.PROCESS_INSTANCE_KEY, null)
              .size(1);
      final SearchRequest searchRequest =
          new SearchRequest(variableAlias).source(searchSourceBuilder);
      final SearchHits hits = esClient.search(searchRequest, RequestOptions.DEFAULT).getHits();
      processInstanceIdWithLotsOfVariables =
          hits.getAt(0).getSourceAsMap().get(VariableTemplate.PROCESS_INSTANCE_KEY).toString();
    } catch (final IOException ex) {
      throw new RuntimeException(
          "Error occurred when reading processInstanceIds from Elasticsearch", ex);
    }
  }

  private void initProcessIds() {
    processIds =
        io.camunda.operate.qa.util.ElasticsearchUtil.getProcessIds(
            esClient, getAlias(processIndex), 2);
  }

  private List<String> requestIdsFor(final SearchRequest searchRequest) throws IOException {
    final SearchHits hits = esClient.search(searchRequest, RequestOptions.DEFAULT).getHits();
    return Arrays.stream(hits.getHits())
        .collect(
            ArrayList::new,
            (list, hit) -> list.add(hit.getId()),
            (list1, list2) -> list1.addAll(list2));
  }

  public void replacePlaceholdersInQuery(final TestQuery testQuery) {
    try {
      // replace in url
      String url = testQuery.getUrl();
      url = replacePlaceholdersInString(url);
      testQuery.setUrl(url);
      // replace in pathParams
      String pathParams = testQuery.getPathParams();
      pathParams = replacePlaceholdersInString(pathParams);
      testQuery.setPathParams(pathParams);
      // replace in body
      String body = testQuery.getBody();
      body = replacePlaceholdersInString(body);
      if (body != null && !body.equals(testQuery.getBody())) {
        testQuery.setBody(objectMapper.readTree(body));
      }
    } catch (final IOException e) {
      throw new RuntimeException("Error occurred when replacing placeholders in queries", e);
    }
  }

  private boolean contains(final String str, final String substr) {
    return str != null && str.contains(substr);
  }

  private String replacePlaceholdersInString(String string) {
    if (contains(string, PROCESS_INSTANCE_IDS_MANY_VARS_PLACEHOLDER)) {
      string =
          replacePlaceholderWithString(
              string,
              PROCESS_INSTANCE_IDS_MANY_VARS_PLACEHOLDER,
              processInstanceIdWithLotsOfVariables);
    }
    if (contains(string, PROCESS_INSTANCE_IDS_PLACEHOLDER)) {
      string =
          replacePlaceholderWithIds(string, PROCESS_INSTANCE_IDS_PLACEHOLDER, processInstanceIds);
    }
    if (contains(string, PROCESS_INSTANCE_ID_PLACEHOLDER)) {
      string =
          replacePlaceholderWithRandomId(
              string, PROCESS_INSTANCE_ID_PLACEHOLDER, processInstanceIds);
    }
    if (contains(string, VARIABLE_ID_PLACEHOLDER)) {
      string =
          replacePlaceholderWithString(
              string,
              VARIABLE_ID_PLACEHOLDER,
              String.format(
                  "%s-%s",
                  processInstanceIds.get(random.nextInt(processInstanceIds.size())), "var1"));
    }
    if (contains(string, PROCESS_IDS_PLACEHOLDER)) {
      string = replacePlaceholderWithIds(string, PROCESS_IDS_PLACEHOLDER, processIds);
    }
    if (contains(string, PROCESS_ID_PLACEHOLDER)) {
      string = replacePlaceholderWithRandomId(string, PROCESS_ID_PLACEHOLDER, processIds);
    }
    if (contains(string, START_DATE_AFTER_PLACEHOLDER)) {
      string = replacePlaceholderWithString(string, START_DATE_AFTER_PLACEHOLDER, startDateAfter);
    }
    if (contains(string, START_DATE_BEFORE_PLACEHOLDER)) {
      string = replacePlaceholderWithString(string, START_DATE_BEFORE_PLACEHOLDER, startDateBefore);
    }
    return string;
  }

  private String replacePlaceholderWithIds(
      String body, final String placeholder, final List<String> ids) {
    final String idsString = ids.stream().collect(Collectors.joining("\",\""));
    body = body.replace(placeholder, idsString);
    return body;
  }

  private String replacePlaceholderWithRandomId(
      final String body, final String placeholder, final List<String> ids) {
    final String id = ids.get(random.nextInt(ids.size()));
    return replacePlaceholderWithString(body, placeholder, id);
  }

  private String replacePlaceholderWithString(
      String body, final String placeholder, final String value) {
    body = body.replace(placeholder, value);
    return body;
  }

  private String getAlias(final IndexDescriptor descriptor) {
    return String.format(
        "%s-%s-%s_alias", prefix, descriptor.getIndexName(), descriptor.getVersion().toLowerCase());
  }
}
