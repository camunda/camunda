package org.camunda.optimize.service.es;

import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/it-applicationContext.xml" })
public class SchemaInitializerIT {

  @Autowired
  private ElasticSearchSchemaInitializer schemaInitializer;

  @Autowired
  @Rule
  public ElasticSearchIntegrationTestRule rule;

  @Test
  public void schemaIsNotInitializedTwice() {

    // throws no errors
    schemaInitializer.initializeSchema();
  }

}
