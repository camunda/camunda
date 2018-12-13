package org.camunda.optimize.service.es.schema;

import org.elasticsearch.common.xcontent.XContentBuilder;

public interface TypeMappingCreator {

  String getType();

  int getVersion();

  XContentBuilder getSource();

}
