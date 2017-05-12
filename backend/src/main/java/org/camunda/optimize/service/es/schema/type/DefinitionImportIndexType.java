package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DefinitionImportIndexType extends StrictTypeMappingCreator {

  public static final String TOTAL_ENTITIES_IMPORTED = "totalEntitiesImported";
  public static final String IMPORT_INDEX_FIELD = "importIndex";
  public static final String CURRENT_PROCESS_DEFINITION = "currentProcessDefinition";
  public static final String ALREADY_IMPORTED_PROCESS_DEFINITIONS = "alreadyImportedProcessDefinitions";

  @Override
  public String getType() {
    return configurationService.getProcessDefinitionImportIndexType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(TOTAL_ENTITIES_IMPORTED)
        .field("type", "integer")
      .endObject()
      .startObject(IMPORT_INDEX_FIELD)
        .field("type", "integer")
      .endObject()
      .startObject(CURRENT_PROCESS_DEFINITION)
        .field("type", "keyword")
      .endObject()
      .startObject(ALREADY_IMPORTED_PROCESS_DEFINITIONS)
        .field("type", "keyword")
      .endObject();
  }
}
