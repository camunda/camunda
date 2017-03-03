package org.camunda.optimize.service.schema;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.ElasticSearchSchemaInitializer;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.schema.type.MyUpdatedEventType;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse.FieldMappingMetaData;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.mapper.StrictDynamicMappingException;
import org.elasticsearch.indices.TypeMissingException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it-applicationContext.xml"})
public class SchemaInitializerIT {

  @Autowired
  public ElasticSearchIntegrationTestRule rule;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ElasticSearchSchemaInitializer schemaInitializer;
  @Autowired
  private ElasticSearchSchemaManager manager;
  @Autowired
  private TransportClient transportClient;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void schemaIsNotInitializedTwice() {
    // given
    rule.cleanAndVerify();

    // when I initialize schema twice
    schemaInitializer.initializeSchema();
    schemaInitializer.initializeSchema();

    // then throws no errors
  }

  @Test
  public void optimizeIndexExistsAfterSchemaInitialization() {
    // given
    rule.cleanAndVerify();

    // when
    schemaInitializer.initializeSchema();

    // then
    assertThat(manager.schemaAlreadyExists(), is(true));
  }

  @Test
  public void typesExistsAfterSchemaInitialization() {
    // given
    rule.cleanAndVerify();

    // when
    schemaInitializer.initializeSchema();

    // then
    assertTypeExists(configurationService.getEventType());
    assertTypeExists(configurationService.getProcessDefinitionType());
    assertTypeExists(configurationService.getProcessDefinitionXmlType());
  }

  private void assertTypeExists(String type) {
    GetMappingsResponse response = transportClient.admin().indices()
        .prepareGetMappings(configurationService.getOptimizeIndex())
        .get();

    boolean containsType = response.mappings()
        .get(configurationService.getOptimizeIndex())
        .containsKey(type);
    assertThat(containsType, is(true));
  }

  @Test
  public void oldMappingsAreUpdated() {

    // given schema is created
    rule.cleanAndVerify();
    schemaInitializer.initializeSchema();

    // when there is a new mapping and I update the mapping
    MyUpdatedEventType updatedEventType = new MyUpdatedEventType(configurationService);
    manager.addMapping(updatedEventType);
    schemaInitializer.initializeSchema();

    // then the mapping contains the new fields
    assertThatNewFieldExists();

  }

  private void assertThatNewFieldExists() {
    GetFieldMappingsResponse response = transportClient.admin().indices()
        .prepareGetFieldMappings(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getEventType())
        .setFields(MyUpdatedEventType.MY_NEW_FIELD)
        .get();

    FieldMappingMetaData fieldEntry =
        response.fieldMappings(
            configurationService.getOptimizeIndex(),
            configurationService.getEventType(),
            MyUpdatedEventType.MY_NEW_FIELD
        );

    assertThat(fieldEntry.isNull(), is(false));
  }

  @Test
  public void newTypeIsNotAddedDynamically() throws IOException {
    // given schema is created
    rule.cleanAndVerify();
    schemaInitializer.initializeSchema();

    // then an exception is thrown
    thrown.expect(TypeMissingException.class);

    // when I add a document to an unknown type
    EventDto eventDto = new EventDto();
    rule.addEntryToElasticsearch("myAwesomeNewType", "12312412", eventDto);
  }

  @Test
  public void onlyAcceptDocumentsThatComplyWithTheSchema() {
    // given schema is created
    rule.cleanAndVerify();
    schemaInitializer.initializeSchema();

    // then
    thrown.expect(StrictDynamicMappingException.class);

    // when we add an event with an undefined type in schema
    ExtendedEventDto extendedEventDto = new ExtendedEventDto();
    rule.addEntryToElasticsearch(configurationService.getEventType(), "12312412", extendedEventDto);
  }

}
