package org.camunda.operate.zeebeimport;

import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.apps.idempotency.ZeebeImportIdempotencyTestConfig;
import org.camunda.operate.TestApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(
  classes = {ZeebeImportIdempotencyTestConfig.class, TestApplication.class},
  properties = {OperateProperties.PREFIX + ".startLoadingDataOnStartup = false", "spring.main.allow-bean-definition-overriding=true"})
public class ZeebeImportIdempotencyIT extends ZeebeImportIT {

  @Autowired
  private ZeebeImportIdempotencyTestConfig.CustomElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @Override
  protected void processAllEvents(int expectedMinEventsCount, ZeebeESImporter.ImportValueType workflowInstance) {
    elasticsearchTestRule.processAllEvents(expectedMinEventsCount, workflowInstance);
    elasticsearchTestRule.processAllEvents(expectedMinEventsCount, workflowInstance);
    elasticsearchBulkProcessor.cancelAttempts();
  }

}