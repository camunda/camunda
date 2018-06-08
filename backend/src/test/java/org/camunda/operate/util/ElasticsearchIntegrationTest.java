package org.camunda.operate.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.camunda.operate.es.types.TypeMappingCreator;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 *
 * @author Svetlana Dorokhova.
 */
@ActiveProfiles("elasticsearch")
public abstract class ElasticsearchIntegrationTest extends OperateIntegrationTest {

  private Logger logger = LoggerFactory.getLogger(ElasticsearchIntegrationTest.class);

  @Autowired
  protected TransportClient esClient;

  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  private List<TypeMappingCreator> typeMappingCreators;

  protected MockMvc mockMvc;

  private HttpMessageConverter mappingJackson2HttpMessageConverter;

  @Autowired
  void setConverters(HttpMessageConverter<?>[] converters) {

    this.mappingJackson2HttpMessageConverter = Arrays.asList(converters).stream()
      .filter(hmc -> hmc instanceof MappingJackson2HttpMessageConverter)
      .findAny()
      .orElse(null);

    assertNotNull("the JSON message converter must not be null",
      this.mappingJackson2HttpMessageConverter);
  }
  @Autowired
  private WebApplicationContext webApplicationContext;

  private boolean haveToClean = true;

  public void starting() {
    this.mockMvc = webAppContextSetup(webApplicationContext).build();
    logger.info("Cleaning elasticsearch...");
    cleanUpElasticSearch();
    logger.info("All documents have been wiped out! Elasticsearch has successfully been cleaned!");
  }

  public void finished() {
    if (haveToClean) {
      logger.info("cleaning up elasticsearch on finish");
      cleanAndVerify();
      refreshIndexesInElasticsearch();
    }
  }

  public void cleanAndVerify() {
    assureElasticsearchIsClean();
    cleanUpElasticSearch();
  }


  public void cleanUpElasticSearch() {
    for (TypeMappingCreator mapping : typeMappingCreators) {
      BulkByScrollResponse response = DeleteByQueryAction.INSTANCE.newRequestBuilder(esClient)
        .refresh(true)
        .filter(matchAllQuery())
        .source(mapping.getType())
        .execute()
        .actionGet();
      logger.info("[{}] documents are removed from the index [{}]", response.getDeleted(), mapping.getType());
    }
  }

  public void refreshIndexesInElasticsearch() {
    try {
      esClient.admin().indices()
        .prepareRefresh()
        .get();
    } catch (IndexNotFoundException e) {
      //nothing to do
    }
  }

  private void assureElasticsearchIsClean() {
    try {
      SearchResponse response = esClient
        .prepareSearch()
        .setQuery(matchAllQuery())
        .get();
      Long hits = response.getHits().getTotalHits();
      assertThat("Elasticsearch was expected to be clean!", hits, is(0L));
    } catch (IndexNotFoundException e) {
      //nothing to do
    }
  }

  public void disableCleanup() {
    this.haveToClean = false;
  }

  protected String json(Object o) throws IOException {
    MockHttpOutputMessage mockHttpOutputMessage = new MockHttpOutputMessage();
    this.mappingJackson2HttpMessageConverter.write(
      o, MediaType.APPLICATION_JSON, mockHttpOutputMessage);
    return mockHttpOutputMessage.getBodyAsString();
  }

}
