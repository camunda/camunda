/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.qa.performance;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
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
  @Autowired private ProcessIndex processIndex;
  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private VariableTemplate variableTemplate;
  private List<String> processInstanceIds = new ArrayList<>();
  private String processInstanceIdWithLotsOfVariables;
  private List<String> processIds = new ArrayList<>();
  private String startDateBefore;
  private String startDateAfter;
  private Random random = new Random();

  @PostConstruct
  public void resolveParameters() throws URISyntaxException {
    URI uri = new URI(elasticsearchUrl);
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
      SearchRequest searchRequest = new SearchRequest(listViewAlias).source(searchSourceBuilder);
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
    } catch (IOException ex) {
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
      SearchRequest searchRequest = new SearchRequest(listViewAlias).source(searchSourceBuilder);
      processInstanceIds = requestIdsFor(searchRequest);
    } catch (IOException ex) {
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
      SearchRequest searchRequest = new SearchRequest(variableAlias).source(searchSourceBuilder);
      final SearchHits hits = esClient.search(searchRequest, RequestOptions.DEFAULT).getHits();
      processInstanceIdWithLotsOfVariables =
          hits.getAt(0).getSourceAsMap().get(VariableTemplate.PROCESS_INSTANCE_KEY).toString();
    } catch (IOException ex) {
      throw new RuntimeException(
          "Error occurred when reading processInstanceIds from Elasticsearch", ex);
    }
  }

  private void initProcessIds() {
    processIds =
        io.camunda.operate.qa.util.ElasticsearchUtil.getProcessIds(
            esClient, getAlias(processIndex), 2);
  }

  private List<String> requestIdsFor(SearchRequest searchRequest) throws IOException {
    final SearchHits hits = esClient.search(searchRequest, RequestOptions.DEFAULT).getHits();
    return Arrays.stream(hits.getHits())
        .collect(
            ArrayList::new,
            (list, hit) -> list.add(hit.getId()),
            (list1, list2) -> list1.addAll(list2));
  }

  public void replacePlaceholdersInQuery(TestQuery testQuery) {
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
    } catch (IOException e) {
      throw new RuntimeException("Error occurred when replacing placeholders in queries", e);
    }
  }

  private boolean contains(String str, String substr) {
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
    return String.format(
        "%s-%s-%s_alias", prefix, descriptor.getIndexName(), descriptor.getVersion().toLowerCase());
  }
}
