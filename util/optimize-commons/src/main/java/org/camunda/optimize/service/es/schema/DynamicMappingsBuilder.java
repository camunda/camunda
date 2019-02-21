package org.camunda.optimize.service.es.schema;

import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;


public class DynamicMappingsBuilder {

  public static XContentBuilder createDynamicSettings(PropertiesAppender appender) throws IOException {
    // @formatter:off
    XContentBuilder content = XContentFactory.jsonBuilder()
      .startObject()
        // false allows us to seamlessly upgrade the schema while keeping old properties to be migrated by a dedicated step
        .field("dynamic", "false")
        .startObject("properties");
          appender.addProperties(content)
        .endObject()
        .startArray("dynamic_templates")
          .startObject()
            .startObject("string_template")
              .field("match_mapping_type","string")
              .field("path_match","*")
              .startObject("mapping")
                .field("type","string")
                .startObject("norms")
                  .field("enabled",false)
                .endObject()
                .field("index_options","docs")
              .endObject()
            .endObject()
          .endObject()
        .endArray()
      .endObject();
    // @formatter:on
    return content;
  }

  public static String createDynamicSettingsAsString() throws IOException {
    XContentBuilder dynamicSettings = createDynamicSettings(a -> a);
    return Strings.toString(dynamicSettings);
  }

}
