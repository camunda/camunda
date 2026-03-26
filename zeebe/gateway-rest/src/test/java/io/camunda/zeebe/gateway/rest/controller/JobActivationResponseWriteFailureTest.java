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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.protocol.model.ActivatedJobResult;
import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.zeebe.gateway.impl.job.JobActivationResult.ActivatedJob;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class JobActivationResponseWriteFailureTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldYieldJobsOnWriteFailure() throws Exception {
    // given
    final List<ActivatedJob> yieldedJobs = new ArrayList<>();
    final List<String> yieldReasons = new ArrayList<>();
    final var result = new CompletableFuture<ResponseEntity<Object>>();

    final var observer =
        new JobActivationRequestResponseObserver(
            result,
            objectMapper,
            (jobs, reason) -> {
              yieldedJobs.addAll(jobs);
              yieldReasons.add(reason);
            });

    final var activationResult = new JobActivationResult();
    activationResult.addJobsItem(new ActivatedJobResult().jobKey("123").retries(3));
    activationResult.addJobsItem(new ActivatedJobResult().jobKey("456").retries(1));
    observer.onNext(activationResult);
    observer.onCompleted();

    // when — simulate Jackson serialization to a failing output stream
    final ResponseEntity<Object> responseEntity = result.get();
    final Object body = responseEntity.getBody();

    final OutputStream failingStream =
        new OutputStream() {
          @Override
          public void write(final int b) throws IOException {
            throw new IOException("Connection reset by peer");
          }
        };

    assertThatThrownBy(
            () -> {
              try (JsonGenerator gen = objectMapper.getFactory().createGenerator(failingStream)) {
                objectMapper.writeValue(gen, body);
              }
            })
        .isInstanceOf(IOException.class);

    // then
    assertThat(yieldedJobs).hasSize(2);
    assertThat(yieldedJobs.get(0).key()).isEqualTo(123L);
    assertThat(yieldedJobs.get(0).retries()).isEqualTo(3);
    assertThat(yieldedJobs.get(1).key()).isEqualTo(456L);
    assertThat(yieldedJobs.get(1).retries()).isEqualTo(1);
    assertThat(yieldReasons).containsExactly("Client disconnected during response write");
  }

  @Test
  void shouldNotYieldWhenNoJobsActivated() throws Exception {
    // given
    final List<ActivatedJob> yieldedJobs = new ArrayList<>();
    final var result = new CompletableFuture<ResponseEntity<Object>>();

    final var observer =
        new JobActivationRequestResponseObserver(
            result, objectMapper, (jobs, reason) -> yieldedJobs.addAll(jobs));

    observer.onCompleted();

    // when — simulate write failure with no jobs
    final ResponseEntity<Object> responseEntity = result.get();
    final Object body = responseEntity.getBody();

    final OutputStream failingStream =
        new OutputStream() {
          @Override
          public void write(final int b) throws IOException {
            throw new IOException("Broken pipe");
          }
        };

    assertThatThrownBy(
            () -> {
              try (JsonGenerator gen = objectMapper.getFactory().createGenerator(failingStream)) {
                objectMapper.writeValue(gen, body);
              }
            })
        .isInstanceOf(IOException.class);

    // then
    assertThat(yieldedJobs).isEmpty();
  }

  @Test
  void shouldWriteSuccessfullyWhenClientConnected() throws Exception {
    // given
    final List<ActivatedJob> yieldedJobs = new ArrayList<>();
    final var result = new CompletableFuture<ResponseEntity<Object>>();

    final var observer =
        new JobActivationRequestResponseObserver(
            result, objectMapper, (jobs, reason) -> yieldedJobs.addAll(jobs));

    final var activationResult = new JobActivationResult();
    activationResult.addJobsItem(new ActivatedJobResult().jobKey("789").retries(2));
    observer.onNext(activationResult);
    observer.onCompleted();

    // when — simulate successful Jackson serialization
    final ResponseEntity<Object> responseEntity = result.get();
    final Object body = responseEntity.getBody();

    final var output = new java.io.ByteArrayOutputStream();
    objectMapper.writeValue(output, body);

    // then
    assertThat(yieldedJobs).isEmpty();
    final String json = output.toString();
    assertThat(json).contains("789");
  }
}
