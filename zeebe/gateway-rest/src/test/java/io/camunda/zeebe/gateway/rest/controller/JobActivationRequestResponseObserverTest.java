/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.camunda.gateway.protocol.model.ActivatedJobResult;
import io.camunda.gateway.protocol.model.JobActivationResult;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class JobActivationRequestResponseObserverTest {

  private CompletableFuture<ResponseEntity<Object>> result;
  private JobActivationRequestResponseObserver observer;

  @BeforeEach
  void setUp() {
    result = new CompletableFuture<>();
    observer = new JobActivationRequestResponseObserver(result);
  }

  @Test
  void shouldAcceptJobsWithoutServletResponse() {
    final JobActivationResult element = new JobActivationResult();
    element.addJobsItem(mock(ActivatedJobResult.class));

    observer.onNext(element);
    // No exception — probe is skipped when servletResponse is null
  }

  @Test
  void shouldAcceptJobsWhenConnectionAlive() throws Exception {
    final HttpServletResponse servletResponse = mock(HttpServletResponse.class);
    final ServletOutputStream outputStream = mock(ServletOutputStream.class);
    when(servletResponse.getOutputStream()).thenReturn(outputStream);
    observer.setServletResponse(servletResponse);

    final JobActivationResult element = new JobActivationResult();
    element.addJobsItem(mock(ActivatedJobResult.class));

    observer.onNext(element);

    verify(outputStream).write(' ');
    verify(outputStream).flush();
  }

  @Test
  void shouldThrowClientDisconnectedExceptionWhenConnectionDead() throws Exception {
    final HttpServletResponse servletResponse = mock(HttpServletResponse.class);
    final ServletOutputStream outputStream = mock(ServletOutputStream.class);
    when(servletResponse.getOutputStream()).thenReturn(outputStream);
    doThrow(new IOException("Broken pipe")).when(outputStream).flush();
    observer.setServletResponse(servletResponse);

    final JobActivationResult element = new JobActivationResult();
    element.addJobsItem(mock(ActivatedJobResult.class));

    assertThatThrownBy(() -> observer.onNext(element))
        .isInstanceOf(ClientDisconnectedException.class)
        .hasMessageContaining("Client disconnected")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldCompleteResultFutureOnCompleted() {
    observer.onCompleted();

    assertThat(result.isDone()).isTrue();
    assertThat(result.join().getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void shouldReturnTrueWhenFutureCancelled() {
    result.cancel(true);
    assertThat(observer.isCancelled()).isTrue();
  }

  @Test
  void shouldReturnFalseWhenFutureNotCancelled() {
    assertThat(observer.isCancelled()).isFalse();
  }
}
