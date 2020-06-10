package io.zeebe.tasklist.data;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.util.ZeebeTestUtil;

@Component
@Profile("dev-data")
public class DevDataGenerator implements DataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(DevDataGenerator.class);

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired
  private TasklistProperties tasklistProperties;

  @Autowired
  private ZeebeClient zeebeClient;

  private Random random = new Random();

  private ExecutorService executor = Executors.newSingleThreadExecutor();

  private boolean shutdown = false;

  @Override
  public void createZeebeDataAsync() {
    if (shouldCreateData()) {
      executor.submit(() -> {
        createZeebeData();
      });
    }
  }

  private void createZeebeData() {
    deployWorkflows();
    startWorkflowInstances();
  }

  private void startWorkflowInstances() {
    final int instancesCount = random.nextInt(50) + 50;
    for (int i = 0; i < instancesCount; i++) {
      startOrderProcess();
      startFlightRegistrationProcess();
    }
  }

  private void startOrderProcess() {
    float price1 = Math.round(random.nextFloat() * 100000) / 100;
    float price2 = Math.round(random.nextFloat() * 10000) / 100;
    ZeebeTestUtil.startWorkflowInstance(zeebeClient, "orderProcess", "{\n"
        + "  \"clientNo\": \"CNT-1211132-02\",\n"
        + "  \"orderNo\": \"CMD0001-01\",\n"
        + "  \"items\": [\n"
        + "    {\n"
        + "      \"code\": \"123.135.625\",\n"
        + "      \"name\": \"Laptop Lenovo ABC-001\",\n"
        + "      \"quantity\": 1,\n"
        + "      \"price\": " + Double.valueOf(price1) + "\n"
        + "    },\n"
        + "    {\n"
        + "      \"code\": \"111.653.365\",\n"
        + "      \"name\": \"Headset Sony QWE-23\",\n"
        + "      \"quantity\": 2,\n"
        + "      \"price\": " + Double.valueOf(price2) + "\n"
        + "    }\n"
        + "  ],\n"
        + "  \"mwst\": " + Double.valueOf((price1 + price2) * 0.19) + ",\n"
        + "  \"total\": " + Double.valueOf((price1 + price2)) + ",\n"
        + "  \"orderStatus\": \"NEW\"\n"
        + "}");
  }


  private void startFlightRegistrationProcess() {
    ZeebeTestUtil.startWorkflowInstance(zeebeClient, "flightRegistration",
        "{\n"
            + "  \"firstName\": \"" + NameGenerator.getRandomFirstName() + "\",\n"
            + "  \"lastName\": \"" + NameGenerator.getRandomLastName() + "\",\n"
            + "  \"passNo\": \"PS" + (random.nextInt(1000000) + (random.nextInt(9) + 1) * 1000000)  + "\",\n"
            + "  \"ticketNo\": \"" + random.nextInt(1000) + "\"\n"
            + "}");
  }

  private void deployWorkflows() {
    ZeebeTestUtil.deployWorkflow(zeebeClient, "orderProcess.bpmn");
    ZeebeTestUtil.deployWorkflow(zeebeClient, "registerPassenger.bpmn");
  }

  public boolean shouldCreateData() {
    try {
      GetIndexRequest request = new GetIndexRequest(tasklistProperties.getZeebeElasticsearch().getPrefix() + "*");
      boolean exists = zeebeEsClient.indices().exists(request, RequestOptions.DEFAULT);
      if (exists) {
        //data already exists
        logger.debug("Data already exists in Zeebe.");
        return false;
      }
    } catch (IOException io) {
      logger.debug("Error occurred while checking existance of data in Zeebe: {}. Demo data won't be created.", io.getMessage());
      return false;
    }
    return true;
  }

  @PreDestroy
  public void shutdown() {
    logger.info("Shutdown DataGenerator");
    shutdown = true;
    if(executor!=null && !executor.isShutdown()) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(200, TimeUnit.MILLISECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
      }
    }
  }
}
