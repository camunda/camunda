package org.camunda.optimize.service.es;

import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

public class ElasticSearchSchemaInitializer {

  @Autowired
  private ElasticSearchSchemaManager schemaManager;

  @PostConstruct
  public void initializeSchema() {
    if (!schemaManager.schemaAlreadyExists()) {
      schemaManager.createOptimizeIndex();
      schemaManager.createMappings();
    }
  }

}
