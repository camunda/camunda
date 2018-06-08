package org.camunda.operate.es.types;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;

public interface TypeMappingCreator {

  String getType();

  XContentBuilder getSource() throws IOException;

}
