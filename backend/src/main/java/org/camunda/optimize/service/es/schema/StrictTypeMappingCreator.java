package org.camunda.optimize.service.es.schema;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public abstract class StrictTypeMappingCreator implements TypeMappingCreator{

  @Autowired
  protected ConfigurationService configurationService;

  private Logger logger = LoggerFactory.getLogger(StrictTypeMappingCreator.class);

  @Override
  public XContentBuilder getSource() {
    XContentBuilder source = null;
    try {
        XContentBuilder content = jsonBuilder()
          .startObject()
            .field("dynamic", "strict")
            .startObject("properties");
              addProperties(content)
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
      source = content;
    } catch (IOException e) {
      String message = "Could not add mapping to the index '" + configurationService.getOptimizeIndex(getType()) +
        "' , type '" + getType() + "'!";
      logger.error(message, e);
    }
    return source;
  }

  protected abstract XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException;
}
