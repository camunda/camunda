package org.camunda.optimize.service.es;

import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class ElasticSearchSchemaInitializer {
  private static final Logger logger = LoggerFactory.getLogger(ElasticSearchSchemaInitializer.class);

  private volatile boolean initialized = false;

  private final ElasticSearchSchemaManager schemaManager;

  @Autowired
  public ElasticSearchSchemaInitializer(ElasticSearchSchemaManager schemaManager) {
    this.schemaManager = schemaManager;
  }

  @PostConstruct
  public void init() {
    initializeSchema();
  }

  public synchronized void initializeSchema() {
    schemaManager.unblockIndices();
    if (!schemaManager.schemaAlreadyExists()) {
      schemaManager.createOptimizeIndices();
    }
    schemaManager.updateMappings();
  }
}
