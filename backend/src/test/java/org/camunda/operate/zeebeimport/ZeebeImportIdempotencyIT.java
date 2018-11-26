package org.camunda.operate.zeebeimport;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.camunda.operate.TestApplication;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.IdTestUtil;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.camunda.operate.zeebeimport.record.RecordImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(
  classes = {ZeebeImportIdempotencyIT.TestConfig.class},
  properties = {OperateProperties.PREFIX + ".startLoadingDataOnStartup = false", "spring.main.allow-bean-definition-overriding=true"})
public class ZeebeImportIdempotencyIT extends ZeebeImportIT {

  /**
   * Let's mock ElasticsearchBulkProcessor.
   */
  @Configuration
  @Import(TestApplication.class) // the actual configuration
  public static class TestConfig {
    @Bean
    @Primary
    public ElasticsearchBulkProcessor elasticsearchBulkProcessor() {
      return new CustomElasticsearchBulkProcessor();
    }

    public static class CustomElasticsearchBulkProcessor extends ElasticsearchBulkProcessor {
      int attempts = 0;

      @Override
      public void persistZeebeRecords(List<? extends RecordImpl> zeebeRecords) throws PersistenceException {
        super.persistZeebeRecords(zeebeRecords);
        if (attempts < 1) {
          attempts++;
          throw new PersistenceException("Fake exception when saving data to Elasticsearch");
        }
      }

      public void cancelAttempts() {
        attempts = 0;
      }
    }

  }

  @Autowired
  private TestConfig.CustomElasticsearchBulkProcessor elasticsearchBulkProcessor;

//  @Autowired
//  private WorkflowInstanceReader workflowInstanceReader;
//
//  @Autowired
//  private WorkflowCache workflowCache;
//
//  @Autowired
//  private ZeebeESImporter zeebeESImporter;
//
//  private ZeebeClient zeebeClient;
//
//  private OffsetDateTime testStartTime;
//
//  @Before
//  public void init() {
//    super.before();
//    testStartTime = OffsetDateTime.now();
//    zeebeClient = super.getClient();
//    try {
//      FieldSetter.setField(workflowCache, WorkflowCache.class.getDeclaredField("zeebeClient"), super.getClient());
//    } catch (NoSuchFieldException e) {
//      fail("Failed to inject ZeebeClient into some of the beans");
//    }
//  }
//
//  @After
//  public void after() {
//    super.after();
//    elasticsearchBulkProcessor.cancelAttempts();
//  }

  @Override
  protected void processAllEvents(int expectedMinEventsCount, ZeebeESImporter.ImportValueType workflowInstance) {
    elasticsearchTestRule.processAllEvents(expectedMinEventsCount, workflowInstance);
    elasticsearchTestRule.processAllEvents(expectedMinEventsCount, workflowInstance);
    elasticsearchBulkProcessor.cancelAttempts();
  }

}