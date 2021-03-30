package org.camunda.operate.qa.util;

import org.camunda.operate.exceptions.MigrationException;
import org.camunda.operate.schema.SchemaStartup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component("schemaStartup")
@Profile("test")
public class TestSchemaStartup extends SchemaStartup {
  private static final Logger logger = LoggerFactory.getLogger(TestSchemaStartup.class);
  @PostConstruct
  @Override
  public void initializeSchema() throws MigrationException {
      logger.info("TestSchemaStartup: no schema will be created, validated or migrated.");
  }
}
