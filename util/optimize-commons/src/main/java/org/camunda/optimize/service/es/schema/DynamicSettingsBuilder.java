package org.camunda.optimize.service.es.schema;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;


public class DynamicSettingsBuilder {

  public static XContentBuilder createDynamicSettings(PropertiesAppender appender) throws IOException {
    XContentBuilder content = XContentFactory.jsonBuilder()
      .startObject()
        .field("dynamic", "strict")
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
    return content;
  }

  public static String createDynamicSettingsAsString() throws IOException {
    XContentBuilder dynamicSettings = createDynamicSettings(a -> a);
    return dynamicSettings.string();
  }

}
