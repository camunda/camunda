/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import io.camunda.tasklist.webapp.service.ProcessService;
import io.camunda.tasklist.webapp.service.TaskService;
import io.camunda.tasklist.zeebe.PartitionHolder;
import io.camunda.tasklist.zeebeimport.ImportPositionHolder;
import io.camunda.zeebe.client.ZeebeClient;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.annotation.Order;
import org.springframework.test.util.ReflectionTestUtils;

public abstract class SessionlessTasklistZeebeIntegrationTest extends TasklistIntegrationTest {
  public static final Boolean IS_ELASTIC = !TasklistPropertiesUtil.isOpenSearchDatabase();

  @Autowired public BeanFactory beanFactory;

  @RegisterExtension
  @Autowired
  @Order(1)
  public DatabaseTestExtension databaseTestExtension;

  @RegisterExtension
  @Autowired
  @Order(2)
  public TasklistZeebeExtension zeebeExtension;

  public ZeebeContainer zeebeContainer;

  @MockBean protected ZeebeClient mockedZeebeClient;
  // we don't want to create ZeebeClient, we will rather use the one from
  // test rule
  protected ZeebeClient zeebeClient;
  @Autowired protected PartitionHolder partitionHolder;
  @Autowired protected ImportPositionHolder importPositionHolder;
  @Autowired protected TasklistProperties tasklistProperties;
  protected TasklistTester tester;
  @Autowired private ProcessCache processCache;
  @Autowired private TaskService taskService;
  @Autowired private ProcessService processService;
  private String workerName;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private ObjectMapper objectMapper;

  private HttpClient httpClient = HttpClient.newHttpClient();

  @BeforeEach
  public void before() {
    super.before();

    zeebeContainer = zeebeExtension.getZeebeContainer();
    assertThat(zeebeContainer).as("zeebeContainer is not null").isNotNull();

    zeebeClient = getClient();
    workerName = TestUtil.createRandomString(10);

    tester = beanFactory.getBean(TasklistTester.class, zeebeClient, databaseTestExtension);

    processCache.clearCache();
    importPositionHolder.cancelScheduledImportPositionUpdateTask().join();
    importPositionHolder.clearCache();
    importPositionHolder.scheduleImportPositionUpdateTask();
    ReflectionTestUtils.setField(partitionHolder, "zeebeClient", getClient());
    ReflectionTestUtils.setField(taskService, "zeebeClient", getClient());
    ReflectionTestUtils.setField(processService, "zeebeClient", getClient());
  }

  @AfterEach
  public void after() {
    processCache.clearCache();
    importPositionHolder.cancelScheduledImportPositionUpdateTask().join();
    importPositionHolder.clearCache();
  }

  public ZeebeClient getClient() {
    return zeebeExtension.getClient();
  }

  public String getWorkerName() {
    return workerName;
  }

  protected void clearMetrics() {
    for (Meter meter : meterRegistry.getMeters()) {
      meterRegistry.remove(meter);
    }
  }

  protected Instant pinZeebeTime() {
    return pinZeebeTime(Instant.now());
  }

  protected Instant pinZeebeTime(Instant pinAt) {
    final var pinRequest = new ZeebeClockActuatorPinRequest(pinAt.toEpochMilli());
    try {
      final var body =
          HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pinRequest));
      return zeebeRequest("POST", "actuator/clock/pin", body);
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException("Could not pin zeebe clock", e);
    }
  }

  protected Instant offsetZeebeTime(Duration offsetBy) {
    final var offsetRequest = new ZeebeClockActuatorOffsetRequest(offsetBy.toMillis());
    try {
      final var body =
          HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(offsetRequest));
      return zeebeRequest("POST", "actuator/clock/pin", body);
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException("Could not offset zeebe clock", e);
    }
  }

  protected Instant resetZeebeTime() {
    try {
      return zeebeRequest("DELETE", "actuator/clock", HttpRequest.BodyPublishers.noBody());
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException("Could not reset zeebe clock", e);
    }
  }

  private Instant zeebeRequest(
      String method, String endpoint, HttpRequest.BodyPublisher bodyPublisher)
      throws IOException, InterruptedException {
    final var fullEndpoint =
        URI.create(
            String.format("http://%s/%s", zeebeContainer.getExternalAddress(9600), endpoint));
    final var httpRequest =
        HttpRequest.newBuilder(fullEndpoint)
            .method(method, bodyPublisher)
            .header("Content-Type", "application/json")
            .build();
    final var httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    if (httpResponse.statusCode() != 200) {
      throw new IllegalStateException("Pinning time failed: " + httpResponse.body());
    }
    final var result =
        objectMapper.readValue(httpResponse.body(), ZeebeClockActuatorResponse.class);

    return Instant.ofEpochMilli(result.epochMilli);
  }

  private static final class ZeebeClockActuatorPinRequest {
    @JsonProperty long epochMilli;

    ZeebeClockActuatorPinRequest(long epochMilli) {
      this.epochMilli = epochMilli;
    }
  }

  private static final class ZeebeClockActuatorOffsetRequest {
    @JsonProperty long epochMilli;

    public ZeebeClockActuatorOffsetRequest(long offsetMilli) {
      this.epochMilli = offsetMilli;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ZeebeClockActuatorResponse {
    @JsonProperty long epochMilli;
  }
}
