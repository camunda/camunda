package org.camunda.optimize.service.es;

import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class ElasticSearchSchemaInitializer {
  private static final Logger logger = LoggerFactory.getLogger(ElasticSearchSchemaInitializer.class);

  private volatile boolean initialized = false;

  private final ElasticSearchSchemaManager schemaManager;
  private final List<StrictTypeMappingCreator> mappings;

  @Autowired
  public ElasticSearchSchemaInitializer(ElasticSearchSchemaManager schemaManager,
                                        List<StrictTypeMappingCreator> mappings) {
    this.schemaManager = schemaManager;
    this.mappings = mappings;
  }

  @PostConstruct
  public void initializeMappings() {
    mappings.forEach(schemaManager::addMapping);
  }

  public synchronized void initializeSchema() {
    if (!initialized) {
      try {
        if (!schemaManager.schemaAlreadyExists()) {
          schemaManager.createOptimizeIndex();
        }
        schemaManager.updateMappings();
        initialized = true;
      } catch (NoNodeAvailableException e) {
        logger.error("can't handle schema initialization\\update", e);
      }

    }
  }

  /**
   * This method has to be invoked before schema initialization can be triggered
   */
  public void useClient(Client instance, ConfigurationService configurationService) {
    schemaManager.setEsclient(instance);
    schemaManager.setConfigurationService(configurationService);
  }

  public boolean isInitialized() {
    return initialized;
  }

  public void setInitialized(boolean initialized) {
    this.initialized = initialized;
  }
}
