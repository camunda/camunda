/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import io.camunda.tasklist.webapp.service.CamundaClientBasedAdapter;
import io.camunda.tasklist.webapp.service.OrganizationService;
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
import org.springframework.core.annotation.Order;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

public abstract class SessionlessTasklistZeebeIntegrationTest extends TasklistIntegrationTest {
  public static final Boolean IS_ELASTIC = TestUtil.isElasticSearch();

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

  @MockitoBean protected OrganizationService organizationService;
  @MockitoBean protected CamundaClient mockedCamundaClient;
  // we don't want to create CamundaClient, we will rather use the one from
  // test rule
  protected CamundaClient camundaClient;
  @Autowired protected TasklistProperties tasklistProperties;
  protected TasklistTester tester;
  @Autowired private CamundaClientBasedAdapter tasklistServicesAdapter;
  @Autowired private ProcessCache processCache;
  private String workerName;
  @Autowired private MeterRegistry meterRegistry;

  @Autowired private ObjectMapper objectMapper;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Override
  @BeforeEach
  public void before() {
    super.before();

    zeebeContainer = zeebeExtension.getZeebeContainer();
    assertThat(zeebeContainer).as("zeebeContainer is not null").isNotNull();

    camundaClient = getClient();
    workerName = TestUtil.createRandomString(10);

    tester = beanFactory.getBean(TasklistTester.class, camundaClient, databaseTestExtension);

    processCache.clearCache();
    ReflectionTestUtils.setField(tasklistServicesAdapter, "camundaClient", getClient());
  }

  @AfterEach
  public void after() {
    processCache.clearCache();
  }

  public CamundaClient getClient() {
    return zeebeExtension.getClient();
  }

  public String getWorkerName() {
    return workerName;
  }

  protected void clearMetrics() {
    for (final Meter meter : meterRegistry.getMeters()) {
      meterRegistry.remove(meter);
    }
  }

  protected Instant pinZeebeTime() {
    return pinZeebeTime(Instant.now());
  }

  protected Instant pinZeebeTime(final Instant pinAt) {
    final var pinRequest = new ZeebeClockActuatorPinRequest(pinAt.toEpochMilli());
    try {
      final var body =
          HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pinRequest));
      return zeebeRequest("POST", "actuator/clock/pin", body);
    } catch (final IOException | InterruptedException e) {
      throw new IllegalStateException("Could not pin zeebe clock", e);
    }
  }

  protected Instant offsetZeebeTime(final Duration offsetBy) {
    final var offsetRequest = new ZeebeClockActuatorOffsetRequest(offsetBy.toMillis());
    try {
      final var body =
          HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(offsetRequest));
      return zeebeRequest("POST", "actuator/clock/pin", body);
    } catch (final IOException | InterruptedException e) {
      throw new IllegalStateException("Could not offset zeebe clock", e);
    }
  }

  protected Instant resetZeebeTime() {
    try {
      return zeebeRequest("DELETE", "actuator/clock", HttpRequest.BodyPublishers.noBody());
    } catch (final IOException | InterruptedException e) {
      throw new IllegalStateException("Could not reset zeebe clock", e);
    }
  }

  private Instant zeebeRequest(
      final String method, final String endpoint, final HttpRequest.BodyPublisher bodyPublisher)
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

    ZeebeClockActuatorPinRequest(final long epochMilli) {
      this.epochMilli = epochMilli;
    }
  }

  private static final class ZeebeClockActuatorOffsetRequest {
    @JsonProperty long epochMilli;

    public ZeebeClockActuatorOffsetRequest(final long offsetMilli) {
      epochMilli = offsetMilli;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ZeebeClockActuatorResponse {
    @JsonProperty long epochMilli;
  }
}
