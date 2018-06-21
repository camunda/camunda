package org.camunda.operate.es.types;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public abstract class StrictTypeMappingCreator implements TypeMappingCreator {

  private Logger logger = LoggerFactory.getLogger(StrictTypeMappingCreator.class);

  public static final String PARTITION_ID = "partitionId";
  public static final String POSITION = "position";

  @Override
  public XContentBuilder getSource() throws IOException {
    //TODO copy-pasted from Optimize, we need to check if this settings suit our needs
    XContentBuilder source = jsonBuilder()
      .startObject()
        .field("dynamic", "strict")
        .startObject("properties");
          addProperties(source)
        .endObject()
        .startObject("_all")
          .field("enabled",false)
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
    return source;
  }

  protected abstract XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException;
}
