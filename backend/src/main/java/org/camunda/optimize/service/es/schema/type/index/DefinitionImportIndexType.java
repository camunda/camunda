package org.camunda.optimize.service.es.schema.type.index;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DefinitionImportIndexType extends StrictTypeMappingCreator {

  public static final String TOTAL_ENTITIES_IMPORTED = "totalEntitiesImported";
  public static final String ALREADY_IMPORTED_PROCESS_DEFINITIONS = "alreadyImportedProcessDefinitions";
  public static final String CURRENT_PROCESS_DEFINITION = "currentProcessDefinition";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String DEFINITION_BASED_IMPORT_INDEX = "definitionBasedImportIndex";
  public static final String MAX_ENTITY_COUNT = "maxEntityCount";
  public static final String ES_TYPE_INDEX_REFERS_TO = "esTypeIndexRefersTo";
  private static final String ENGINE = "engine";

  @Override
  public String getType() {
    return configurationService.getProcessDefinitionImportIndexType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(ENGINE)
        .field("type", "keyword")
      .endObject()
      .startObject(ES_TYPE_INDEX_REFERS_TO)
        .field("type", "keyword")
      .endObject()
      .startObject(TOTAL_ENTITIES_IMPORTED)
        .field("type", "long")
      .endObject()
      .startObject(CURRENT_PROCESS_DEFINITION)
        .field("type", "nested")
        .startObject("properties")
          .startObject(PROCESS_DEFINITION_ID)
            .field("type", "keyword")
          .endObject()
          .startObject(DEFINITION_BASED_IMPORT_INDEX)
            .field("type", "long")
          .endObject()
          .startObject(MAX_ENTITY_COUNT)
            .field("type", "long")
          .endObject()
        .endObject()
      .endObject()
      .startObject(ALREADY_IMPORTED_PROCESS_DEFINITIONS)
        .field("type", "nested")
        .startObject("properties")
          .startObject(PROCESS_DEFINITION_ID)
            .field("type", "keyword")
          .endObject()
          .startObject(DEFINITION_BASED_IMPORT_INDEX)
            .field("type", "long")
          .endObject()
          .startObject(MAX_ENTITY_COUNT)
            .field("type", "long")
          .endObject()
        .endObject()
      .endObject();
  }
}
