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

import io.camunda.operate.OperateProfileService;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.webapp.rest.exception.Error;
import io.camunda.operate.webapp.rest.exception.InternalAPIException;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class InternalAPIErrorControllerTest {

  private static final String EXCEPTION_MESSAGE = "profile exception message";
  @Mock private OperateProfileService mockProfileService;
  private InternalAPIErrorController underTest;

  @BeforeEach
  public void setup() {
    underTest = new InternalAPIErrorController() {};

    ReflectionTestUtils.setField(underTest, "operateProfileService", mockProfileService);
    when(mockProfileService.getMessageByProfileFor(any())).thenReturn(EXCEPTION_MESSAGE);
  }

  @Test
  public void testHandleOperateRuntimeException() {
    final OperateRuntimeException exception = new OperateRuntimeException("runtime exception");

    final ResponseEntity<Error> result = underTest.handleOperateRuntimeException(exception);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

    final Error errorBody = result.getBody();

    assertThat(errorBody).isNotNull();
    assertThat(errorBody.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(errorBody.getMessage()).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(errorBody.getInstance()).isNull();
  }

  @Test
  public void testHandleRuntimeNotFoundException() {
    final io.camunda.operate.store.NotFoundException exception =
        new io.camunda.operate.store.NotFoundException("not found exception");

    final ResponseEntity<Error> result = underTest.handleRuntimeNotFoundException(exception);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

    final Error errorBody = result.getBody();

    assertThat(errorBody).isNotNull();
    assertThat(errorBody.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(errorBody.getMessage()).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(errorBody.getInstance()).isNull();
  }

  @Test
  public void testHandleInternalAPIException() {
    final InternalAPIException exception = new InternalAPIException("internal api exception") {};
    exception.setInstance("instanceId");

    final ResponseEntity<Error> result = underTest.handleInternalAPIException(exception);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

    final Error errorBody = result.getBody();

    assertThat(errorBody).isNotNull();
    assertThat(errorBody.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(errorBody.getMessage()).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(errorBody.getInstance()).isEqualTo(exception.getInstance());
  }

  @Test
  public void testHandleInternalNotFoundException() {
    final NotFoundException exception = new NotFoundException("not found exception");
    exception.setInstance("instanceId");

    final ResponseEntity<Error> result = underTest.handleInternalNotFoundException(exception);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

    final Error errorBody = result.getBody();

    assertThat(errorBody).isNotNull();
    assertThat(errorBody.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(errorBody.getMessage()).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(errorBody.getInstance()).isEqualTo(exception.getInstance());
  }

  @Test
  public void testHandleNotAuthorizedException() {
    final NotAuthorizedException exception = new NotAuthorizedException("not authorized exception");
    exception.setInstance("instanceId");

    final ResponseEntity<Error> result = underTest.handleNotAuthorizedException(exception);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

    final Error errorBody = result.getBody();

    assertThat(errorBody).isNotNull();
    assertThat(errorBody.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(errorBody.getMessage()).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(errorBody.getInstance()).isEqualTo(exception.getInstance());
  }
}
