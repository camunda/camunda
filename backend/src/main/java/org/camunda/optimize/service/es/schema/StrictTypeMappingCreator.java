package org.camunda.optimize.service.es.schema;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.DynamicSettingsBuilder.createDynamicSettings;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public abstract class StrictTypeMappingCreator implements TypeMappingCreator, PropertiesAppender {

  @Autowired
  protected ConfigurationService configurationService;

  private Logger logger = LoggerFactory.getLogger(StrictTypeMappingCreator.class);

  @Override
  public XContentBuilder getSource() {
    XContentBuilder source = null;
    try {
      source = createDynamicSettings(this);
    } catch (IOException e) {
      String message = "Could not add mapping to the index '" + configurationService.getOptimizeIndex(getType()) +
        "' , type '" + getType() + "'!";
      logger.error(message, e);
    }
    return source;
  }
}
