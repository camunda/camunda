package org.camunda.operate.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.camunda.operate.es.types.TypeMappingCreator;
import org.camunda.operate.es.writer.ElasticsearchBulkProcessor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;


public class ElasticsearchTestRule extends ExternalResource {

  private Logger logger = LoggerFactory.getLogger(ElasticsearchTestRule.class);

  @Autowired
  protected TransportClient esClient;

  @Autowired
  @Qualifier("esObjectMapper")
  protected ObjectMapper objectMapper;

  @Autowired
  private List<TypeMappingCreator> typeMappingCreators;

  private MockMvc mockMvc;

  private HttpMessageConverter mappingJackson2HttpMessageConverter;

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

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

  @Override
  public void before() {
    this.mockMvc = webAppContextSetup(webApplicationContext).build();
    logger.info("Cleaning elasticsearch...");
    cleanUpElasticSearch();
    logger.info("All documents have been wiped out! Elasticsearch has successfully been cleaned!");
  }

  @Override
  public void after() {
    if (haveToClean) {
      logger.info("cleaning up elasticsearch on finish");
      cleanAndVerify();
      refreshIndexesInElasticsearch();
    }
  }

  public void cleanAndVerify() {
//    assureElasticsearchIsClean();
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

  public void processAllEvents() {
    processAllEvents(1);
  }

  public void processAllEvents(int expectedMinEventsCount) {
    try {
      int entitiesCount;
      int totalCount = 0;
      int emptyAttempts = 0;
      do {
        Thread.sleep(100L);
        entitiesCount = elasticsearchBulkProcessor.processNextEntitiesBatch();
        totalCount += entitiesCount;
        if (entitiesCount > 0) {
          emptyAttempts = 0;
        } else {
          emptyAttempts++;
        }
      } while(entitiesCount > 0 && totalCount < expectedMinEventsCount && emptyAttempts <= 3);
    } catch (InterruptedException e) {
      e.printStackTrace();
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

  public String json(Object o) throws IOException {
    MockHttpOutputMessage mockHttpOutputMessage = new MockHttpOutputMessage();
    this.mappingJackson2HttpMessageConverter.write(
      o, MediaType.APPLICATION_JSON, mockHttpOutputMessage);
    return mockHttpOutputMessage.getBodyAsString();
  }

  public MockMvc getMockMvc() {
    return mockMvc;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
