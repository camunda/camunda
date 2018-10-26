package org.camunda.operate.es.types;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;

public interface TypeMappingCreator {

  String getIndexName();

  String getAlias();

  XContentBuilder getSource() throws IOException;

}
