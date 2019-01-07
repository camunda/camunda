package org.camunda.optimize.service.schema;

import com.jayway.jsonpath.JsonPath;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.schema.type.MyUpdatedEventType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse.FieldMappingMetaData;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getVersionedOptimizeIndexNameForTypeMapping;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SchemaInitializerIT {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  public static ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public static EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @ClassRule
  public static RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Before
  public void setUp() {
    // given
    elasticSearchRule.cleanAndVerify();
  }

  @Test
  public void schemaIsNotInitializedTwice() {

    // when I initialize schema twice
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // then throws no errors
  }

  @Test
  public void optimizeIndexExistsAfterSchemaInitialization() {

    // when
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // then
    assertThat(embeddedOptimizeRule.getElasticSearchSchemaManager().schemaAlreadyExists(), is(true));
  }

  @Test
  public void typesExistsAfterSchemaInitialization() throws IOException {

    // when
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // then
    assertTypeExists(ElasticsearchConstants.METADATA_TYPE);
    assertTypeExists(ElasticsearchConstants.PROC_INSTANCE_TYPE);
    assertTypeExists(ElasticsearchConstants.IMPORT_INDEX_TYPE);
    assertTypeExists(ElasticsearchConstants.PROC_DEF_TYPE);
  }

  private void assertTypeExists(String type) throws IOException {
    final String optimizeIndexAliasForType = getOptimizeIndexAliasForType(type);

    RestClient esClient = elasticSearchRule.getEsClient().getLowLevelClient();
    Request request = new Request("GET", "/" + optimizeIndexAliasForType + "/_mapping");
    Response response = esClient.performRequest(request);

    String responseBody = EntityUtils.toString(response.getEntity());
    Map<String, Map<String, Map<String, Object>>> mappings = JsonPath.read(responseBody, "$");

    assertThat(mappings.size(), is(1));

    boolean containsType = mappings
      .values()
      .iterator()
      .next()
      .get("mappings")
      .keySet()
      .contains(type);
    assertThat(containsType, is(true));
  }

  @Test
  public void oldMappingsAreUpdated() throws IOException {

    // given schema is created
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // when there is a new mapping and I update the mapping
    MyUpdatedEventType updatedEventType = new MyUpdatedEventType(embeddedOptimizeRule.getConfigurationService());
    embeddedOptimizeRule.getElasticSearchSchemaManager().addMapping(updatedEventType);
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // then the mapping contains the new fields
    assertThatNewFieldExists();
  }

  private void assertThatNewFieldExists() throws IOException {
    final String metaDataType = ElasticsearchConstants.METADATA_TYPE;
    final String optimizeIndexAliasForType = getOptimizeIndexAliasForType(metaDataType);

    GetFieldMappingsRequest request = new GetFieldMappingsRequest().indices(optimizeIndexAliasForType)
      .indices(optimizeIndexAliasForType)
      .types(metaDataType)
      .fields(MyUpdatedEventType.MY_NEW_FIELD);
    GetFieldMappingsResponse response =
      embeddedOptimizeRule.getElasticsearchClient().indices().getFieldMapping(request, RequestOptions.DEFAULT);

    final MyUpdatedEventType updatedEventType = new MyUpdatedEventType(embeddedOptimizeRule.getConfigurationService());
    final FieldMappingMetaData fieldEntry =
      response.fieldMappings(
        getVersionedOptimizeIndexNameForTypeMapping(updatedEventType),
        metaDataType,
        MyUpdatedEventType.MY_NEW_FIELD
      );

    assertThat(fieldEntry.isNull(), is(false));
  }

  @Test
  public void newIndexIsNotAddedDynamically() {
    // given schema is created
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // then an exception is thrown
    thrown.expect(ElasticsearchStatusException.class);

    // when I add a document to an unknown type
    FlowNodeEventDto flowNodeEventDto = new FlowNodeEventDto();
    elasticSearchRule.addEntryToElasticsearch("myAwesomeNewIndex", "12312412", flowNodeEventDto);
  }

  @Test
  public void onlyAcceptDocumentsThatComplyWithTheSchema() {
    // given schema is created
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // then
    thrown.expect(ElasticsearchStatusException.class);

    // when we add an event with an undefined type in schema
    ExtendedFlowNodeEventDto extendedEventDto = new ExtendedFlowNodeEventDto();
    elasticSearchRule.addEntryToElasticsearch(
      ElasticsearchConstants.METADATA_TYPE,
      "12312412",
      extendedEventDto
    );
  }

}
