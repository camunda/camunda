package org.camunda.optimize.upgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.TypeMappingCreator;
import org.camunda.optimize.service.es.schema.type.MetadataType;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.util.List;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public abstract class AbstractUpgradeIT {

  public static final MetadataType METADATA_TYPE = new MetadataType();

  protected ObjectMapper objectMapper;
  protected RestHighLevelClient restClient;

  @Before
  protected void setUp() throws Exception {
    if (objectMapper == null) {
      objectMapper = new ObjectMapperFactory(
        new OptimizeDateTimeFormatterFactory().getObject(),
        new ConfigurationService()
      ).createOptimizeMapper();
    }
    if (restClient == null) {
      restClient = ElasticsearchHighLevelRestClientBuilder.build(new ConfigurationService());
    }
    cleanAllDataFromElasticsearch();
    createEmptyEnvConfig();
  }

  @After
  public void after() throws Exception {
    cleanAllDataFromElasticsearch();
    deleteEnvConfig();
  }

  public void initSchema(List<TypeMappingCreator> mappingCreators) {
    final ElasticSearchSchemaManager elasticSearchSchemaManager = new ElasticSearchSchemaManager(
      new ConfigurationService(), mappingCreators, objectMapper
    );
    elasticSearchSchemaManager.initializeSchema(restClient);
  }

  protected void addVersionToElasticsearch(String version) throws IOException {
    final IndexRequest indexRequest = new IndexRequest(
      getOptimizeIndexAliasForType(METADATA_TYPE.getType()),
      METADATA_TYPE.getType(),
      "1"
    );
    indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
    indexRequest.source(ImmutableMap.of(MetadataType.SCHEMA_VERSION, version), XContentType.JSON);

    final IndexResponse index = restClient.index(indexRequest, RequestOptions.DEFAULT);
    assertThat(index.status().getStatus(), is(201));
  }

  protected void cleanAllDataFromElasticsearch() {
    try {
      restClient.indices().delete(new DeleteIndexRequest("_all"), RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new RuntimeException("Failed cleaning elasticsearch");
    }
  }

  protected void executeBulk(final String bulkPayload) throws IOException {
    final Request request = new Request(HttpPost.METHOD_NAME, "_bulk");
    final HttpEntity entity = new NStringEntity(
      SchemaUpgradeUtil.readClasspathFileAsString(bulkPayload),
      ContentType.APPLICATION_JSON
    );
    request.setEntity(entity);
    restClient.getLowLevelClient().performRequest(request);
    restClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
  }

}
