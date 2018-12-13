package org.camunda.operate.es.schema.indices;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;

public interface IndexCreator {

  String getIndexName();

  String getAlias();

  XContentBuilder getSource() throws IOException;

}
