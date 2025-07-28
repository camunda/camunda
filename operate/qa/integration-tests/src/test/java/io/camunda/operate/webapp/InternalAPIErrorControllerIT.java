/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.exception.InternalAPIException;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.transform.DataAggregator;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Utilizes an endpoint from OperationRestService to test the error handling functionality
// of the abstract InternalAPIErrorController class
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class, UnifiedConfigurationHelper.class, UnifiedConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      OperateProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"
    })
@ActiveProfiles({"test"})
@AutoConfigureMockMvc
public class InternalAPIErrorControllerIT {
  private static final String EXCEPTION_MESSAGE = "profile exception message";
  @MockBean DataAggregator dataAggregator;
  @Autowired private MockMvc mockMvc;
  @MockBean private OperationReader operationReader;
  @MockBean private OperateProfileService mockProfileService;

  @Autowired private ObjectMapper objectMapper;

  private MockHttpServletRequestBuilder mockGetRequest;

  @Before
  public void setup() {
    mockGetRequest = get("/api/operations").queryParam("batchOperationId", "abc");
    when(mockProfileService.getMessageByProfileFor(any())).thenReturn(EXCEPTION_MESSAGE);
  }

  @Test
  public void shouldReturn500ForOperateRuntimeException() throws Exception {
    final OperateRuntimeException exception = new OperateRuntimeException("runtime exception");

    when(operationReader.getOperationsByBatchOperationId(any())).thenThrow(exception);

    final MvcResult result = mockMvc.perform(mockGetRequest).andReturn();

    assertThat(result.getResponse().getStatus())
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());

    final Map<String, Object> resultBody =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

    assertThat(resultBody.get("status")).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(resultBody.get("message")).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(resultBody.get("instance")).isNull();
  }

  @Test
  public void shouldReturn404ForRuntimeNotFoundException() throws Exception {
    final io.camunda.operate.store.NotFoundException exception =
        new io.camunda.operate.store.NotFoundException("not found exception");

    when(operationReader.getOperationsByBatchOperationId(any())).thenThrow(exception);

    final MvcResult result = mockMvc.perform(mockGetRequest).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());

    final Map<String, Object> resultBody =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

    assertThat(resultBody.get("status")).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(resultBody.get("message")).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(resultBody.get("instance")).isNull();
  }

  @Test
  public void shouldReturn400ForInternalAPIException() throws Exception {
    final InternalAPIException exception = new InternalAPIException("internal api exception") {};
    exception.setInstance("instanceId");

    when(operationReader.getOperationsByBatchOperationId(any())).thenThrow(exception);

    final MvcResult result = mockMvc.perform(mockGetRequest).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());

    final Map<String, Object> resultBody =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

    assertThat(resultBody.get("status")).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(resultBody.get("message")).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(resultBody.get("instance")).isEqualTo(exception.getInstance());
  }

  @Test
  public void shouldReturn404ForInternalNotFoundException() throws Exception {
    final NotFoundException exception = new NotFoundException("not found exception");
    exception.setInstance("instanceId");

    when(operationReader.getOperationsByBatchOperationId(any())).thenThrow(exception);

    final MvcResult result = mockMvc.perform(mockGetRequest).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());

    final Map<String, Object> resultBody =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

    assertThat(resultBody.get("status")).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(resultBody.get("message")).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(resultBody.get("instance")).isEqualTo(exception.getInstance());
  }

  @Test
  public void shouldReturn403ForNotAuthorizedException() throws Exception {
    final NotAuthorizedException exception = new NotAuthorizedException("not authorized exception");
    exception.setInstance("instanceId");

    when(operationReader.getOperationsByBatchOperationId(any())).thenThrow(exception);

    final MvcResult result = mockMvc.perform(mockGetRequest).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());

    final Map<String, Object> resultBody =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

    assertThat(resultBody.get("status")).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(resultBody.get("message")).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(resultBody.get("instance")).isEqualTo(exception.getInstance());
  }
}
