/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.TasklistZeebeIntegrationTest.DEFAULT_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.UserReader;
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
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

@WithMockUser(DEFAULT_USER_ID)
public abstract class TasklistZeebeIntegrationTest extends TasklistIntegrationTest {

  public static final String DEFAULT_USER_ID = "demo";
  public static final String DEFAULT_DISPLAY_NAME = "Demo User";
  @Autowired public BeanFactory beanFactory;
  @Rule public final TasklistZeebeRule zeebeRule;
  public ZeebeContainer zeebeContainer;
  @Rule public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @MockBean protected ZeebeClient mockedZeebeClient;
  // we don't want to create ZeebeClient, we will rather use the one from
  // test rule
  protected ZeebeClient zeebeClient;
  @Autowired protected PartitionHolder partitionHolder;
  @Autowired protected ImportPositionHolder importPositionHolder;
  @Autowired protected TasklistProperties tasklistProperties;
  protected TasklistTester tester;
  @MockBean protected UserReader userReader;
  @Autowired private ProcessCache processCache;
  private String workerName;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private ObjectMapper objectMapper;

  private HttpClient httpClient = HttpClient.newHttpClient();

  public TasklistZeebeIntegrationTest() {
    zeebeRule = new TasklistZeebeRule();
  }

  @Before
  public void before() {
    super.before();

    zeebeContainer = zeebeRule.getZeebeContainer();
    assertThat(zeebeContainer).as("zeebeContainer is not null").isNotNull();

    zeebeClient = getClient();
    workerName = TestUtil.createRandomString(10);

    tester = beanFactory.getBean(TasklistTester.class, zeebeClient, elasticsearchTestRule);

    processCache.clearCache();
    importPositionHolder.cancelScheduledImportPositionUpdateTask().join();
    importPositionHolder.clearCache();
    importPositionHolder.scheduleImportPositionUpdateTask();
    partitionHolder.setZeebeClient(getClient());

    setDefaultCurrentUser();
  }

  protected void setDefaultCurrentUser() {
    setCurrentUser(getDefaultCurrentUser());
  }

  protected UserDTO getDefaultCurrentUser() {
    return new UserDTO()
        .setUserId(DEFAULT_USER_ID)
        .setDisplayName(DEFAULT_DISPLAY_NAME)
        .setPermissions(List.of(Permission.WRITE));
  }

  protected void setCurrentUser(UserDTO user) {
    Mockito.when(userReader.getCurrentUserId()).thenReturn(user.getUserId());
    Mockito.when(userReader.getCurrentUser()).thenReturn(user);
    Mockito.when(userReader.getUsersByUsernames(any())).thenReturn(List.of(user));
    final String organisation =
        user.getUserId().equals(DEFAULT_USER_ID)
            ? UserReader.DEFAULT_ORGANIZATION
            : user.getUserId() + "-org";
    Mockito.when(userReader.getCurrentOrganizationId()).thenReturn(organisation);
  }

  @After
  public void after() {
    setDefaultCurrentUser();
    processCache.clearCache();
    importPositionHolder.cancelScheduledImportPositionUpdateTask().join();
    importPositionHolder.clearCache();
  }

  public ZeebeClient getClient() {
    return zeebeRule.getClient();
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
