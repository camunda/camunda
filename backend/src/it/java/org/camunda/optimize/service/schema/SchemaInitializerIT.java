package org.camunda.optimize.service.schema;

import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.es.ElasticSearchSchemaInitializer;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.schema.type.MyUpdatedEventType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.spring.OptimizeAwareDependencyInjectionListener;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse.FieldMappingMetaData;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.mapper.StrictDynamicMappingException;
import org.elasticsearch.indices.TypeMissingException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.ServletTestExecutionListener;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration( locations = {"/it/it-applicationContext.xml"})
@TestExecutionListeners({
    ServletTestExecutionListener.class,
    OptimizeAwareDependencyInjectionListener.class,
    DirtiesContextTestExecutionListener.class,
    TransactionalTestExecutionListener.class
})
public class SchemaInitializerIT {

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ElasticSearchSchemaInitializer schemaInitializer;
  @Autowired
  private ElasticSearchSchemaManager manager;
  @Autowired
  private Client transportClient;

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
    schemaInitializer.initializeSchema();
    schemaInitializer.initializeSchema();

    // then throws no errors
  }

  @Test
  public void optimizeIndexExistsAfterSchemaInitialization() {

    // when
    schemaInitializer.initializeSchema();

    // then
    assertThat(manager.schemaAlreadyExists(), is(true));
  }

  @Test
  public void typesExistsAfterSchemaInitialization() {

    // when
    schemaInitializer.initializeSchema();

    // then
    assertTypeExists(configurationService.getEventType());
    assertTypeExists(configurationService.getProcessInstanceType());
    assertTypeExists(configurationService.getDurationHeatmapTargetValueType());
    assertTypeExists(configurationService.getImportIndexType());
    assertTypeExists(configurationService.getProcessDefinitionType());
    assertTypeExists(configurationService.getProcessDefinitionXmlType());
    assertTypeExists(configurationService.getVariableType());
  }

  private void assertTypeExists(String type) {
    GetMappingsResponse response = transportClient.admin().indices()
        .prepareGetMappings(configurationService.getOptimizeIndex(type))
        .get();

    boolean containsType = response.mappings()
        .get(configurationService.getOptimizeIndex(type))
        .containsKey(type);
    assertThat(containsType, is(true));
  }

  @Test
  public void oldMappingsAreUpdated() {

    // given schema is created
    schemaInitializer.initializeSchema();

    // when there is a new mapping and I update the mapping
    MyUpdatedEventType updatedEventType = new MyUpdatedEventType(configurationService);
    manager.addMapping(updatedEventType);
    schemaInitializer.setInitialized(false);
    schemaInitializer.initializeSchema();

    // then the mapping contains the new fields
    assertThatNewFieldExists();
  }

  private void assertThatNewFieldExists() {
    GetFieldMappingsResponse response = transportClient.admin().indices()
        .prepareGetFieldMappings(configurationService.getOptimizeIndex(configurationService.getEventType()))
        .setTypes(configurationService.getEventType())
        .setFields(MyUpdatedEventType.MY_NEW_FIELD)
        .get();

    FieldMappingMetaData fieldEntry =
        response.fieldMappings(
            configurationService.getOptimizeIndex(configurationService.getEventType()),
            configurationService.getEventType(),
            MyUpdatedEventType.MY_NEW_FIELD
        );

    assertThat(fieldEntry.isNull(), is(false));
  }

  @Test
  public void newTypeIsNotAddedDynamically() {
    // given schema is created
    schemaInitializer.initializeSchema();

    // then an exception is thrown
    thrown.expect(IndexNotFoundException.class);

    // when I add a document to an unknown type
    FlowNodeEventDto flowNodeEventDto = new FlowNodeEventDto();
    elasticSearchRule.addEntryToElasticsearch("myAwesomeNewType", "12312412", flowNodeEventDto);
  }

  @Test
  public void onlyAcceptDocumentsThatComplyWithTheSchema() {
    // given schema is created
    schemaInitializer.initializeSchema();

    // then
    thrown.expect(StrictDynamicMappingException.class);

    // when we add an event with an undefined type in schema
    ExtendedFlowNodeEventDto extendedEventDto = new ExtendedFlowNodeEventDto();
    elasticSearchRule.addEntryToElasticsearch(configurationService.getEventType(), "12312412", extendedEventDto);
  }

}
