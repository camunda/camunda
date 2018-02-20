package org.camunda.optimize.service.schema;

import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.schema.type.MyUpdatedEventType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
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
    assertTypeExists(embeddedOptimizeRule.getConfigurationService().getEventType());
    assertTypeExists(embeddedOptimizeRule.getConfigurationService().getProcessInstanceType());
    assertTypeExists(embeddedOptimizeRule.getConfigurationService().getDurationHeatmapTargetValueType());
    assertTypeExists(embeddedOptimizeRule.getConfigurationService().getImportIndexType());
    assertTypeExists(embeddedOptimizeRule.getConfigurationService().getProcessDefinitionType());
    assertTypeExists(embeddedOptimizeRule.getConfigurationService().getProcessDefinitionXmlType());
    assertTypeExists(embeddedOptimizeRule.getConfigurationService().getVariableType());
  }

  private void assertTypeExists(String type) {
    GetMappingsResponse response = embeddedOptimizeRule.getTransportClient().admin().indices()
        .prepareGetMappings(embeddedOptimizeRule.getConfigurationService().getOptimizeIndex(type))
        .get();

    boolean containsType = response.mappings()
        .get(embeddedOptimizeRule.getConfigurationService().getOptimizeIndex(type))
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
    embeddedOptimizeRule.getSchemaInitializer().setInitialized(false);
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // then the mapping contains the new fields
    assertThatNewFieldExists();
  }

  private void assertThatNewFieldExists() {
    GetFieldMappingsResponse response = embeddedOptimizeRule.getTransportClient().admin().indices()
        .prepareGetFieldMappings(embeddedOptimizeRule.getConfigurationService().getOptimizeIndex(embeddedOptimizeRule.getConfigurationService().getEventType()))
        .setTypes(embeddedOptimizeRule.getConfigurationService().getEventType())
        .setFields(MyUpdatedEventType.MY_NEW_FIELD)
        .get();

    FieldMappingMetaData fieldEntry =
        response.fieldMappings(
            embeddedOptimizeRule.getConfigurationService().getOptimizeIndex(embeddedOptimizeRule.getConfigurationService().getEventType()),
            embeddedOptimizeRule.getConfigurationService().getEventType(),
            MyUpdatedEventType.MY_NEW_FIELD
        );

    assertThat(fieldEntry.isNull(), is(false));
  }

  @Test
  public void newTypeIsNotAddedDynamically() {
    // given schema is created
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // then an exception is thrown
    thrown.expect(IndexNotFoundException.class);

    // when I add a document to an unknown type
    FlowNodeEventDto flowNodeEventDto = new FlowNodeEventDto();
    elasticSearchRule.addEntryToElasticsearch("myAwesomeNewType", "12312412", flowNodeEventDto);
  }

  @Test
  public void onlyAcceptDocumentsThatComplyWithTheSchema() {
    // given schema is created
    embeddedOptimizeRule.getSchemaInitializer().initializeSchema();

    // then
    thrown.expect(StrictDynamicMappingException.class);

    // when we add an event with an undefined type in schema
    ExtendedFlowNodeEventDto extendedEventDto = new ExtendedFlowNodeEventDto();
    elasticSearchRule.addEntryToElasticsearch(embeddedOptimizeRule.getConfigurationService().getEventType(), "12312412", extendedEventDto);
  }

}
