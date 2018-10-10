package org.camunda.optimize.service.es.schema;

import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public interface PropertiesAppender {

  XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException;
}
