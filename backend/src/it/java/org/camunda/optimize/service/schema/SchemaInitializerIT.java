package org.camunda.optimize.service.schema;

import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.schema.type.MyUpdatedEventType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse.FieldMappingMetaData;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.mapper.StrictDynamicMappingException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

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
  public void typesExistsAfterSchemaInitialization() {

    // when
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // then
    assertTypeExists(ElasticsearchConstants.METADATA_TYPE);
    assertTypeExists(ElasticsearchConstants.PROC_INSTANCE_TYPE);
    assertTypeExists(ElasticsearchConstants.IMPORT_INDEX_TYPE);
    assertTypeExists(ElasticsearchConstants.PROC_DEF_TYPE);
  }

  private void assertTypeExists(String type) {
    final String optimizeIndexAliasForType = getOptimizeIndexAliasForType(type);
    final GetMappingsResponse response = embeddedOptimizeRule.getTransportClient().admin().indices()
      .prepareGetMappings(optimizeIndexAliasForType)
      .get();

    assertThat(response.mappings().size(), is(1));

    boolean containsType = response.mappings()
      .valuesIt()
      .next()
      .containsKey(type);
    assertThat(containsType, is(true));
  }

  @Test
  public void oldMappingsAreUpdated() {

    // given schema is created
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // when there is a new mapping and I update the mapping
    MyUpdatedEventType updatedEventType = new MyUpdatedEventType(embeddedOptimizeRule.getConfigurationService());
    embeddedOptimizeRule.getElasticSearchSchemaManager().addMapping(updatedEventType);
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // then the mapping contains the new fields
    assertThatNewFieldExists();
  }

  private void assertThatNewFieldExists() {
    final String metaDataType = ElasticsearchConstants.METADATA_TYPE;
    final String optimizeIndexAliasForType = getOptimizeIndexAliasForType(metaDataType);
    final GetFieldMappingsResponse response = embeddedOptimizeRule.getTransportClient().admin().indices()
      .prepareGetFieldMappings(optimizeIndexAliasForType)
      .setTypes(metaDataType)
      .setFields(MyUpdatedEventType.MY_NEW_FIELD)
      .get();

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
    thrown.expect(IndexNotFoundException.class);

    // when I add a document to an unknown type
    FlowNodeEventDto flowNodeEventDto = new FlowNodeEventDto();
    elasticSearchRule.addEntryToElasticsearch("myAwesomeNewIndex", "12312412", flowNodeEventDto);
  }

  @Test
  public void onlyAcceptDocumentsThatComplyWithTheSchema() {
    // given schema is created
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // then
    thrown.expect(StrictDynamicMappingException.class);

    // when we add an event with an undefined type in schema
    ExtendedFlowNodeEventDto extendedEventDto = new ExtendedFlowNodeEventDto();
    elasticSearchRule.addEntryToElasticsearch(
      ElasticsearchConstants.METADATA_TYPE,
      "12312412",
      extendedEventDto
    );
  }

}
