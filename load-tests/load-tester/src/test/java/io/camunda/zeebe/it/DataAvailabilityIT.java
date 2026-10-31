/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.zeebe.LoadTesterApplication;
import io.camunda.zeebe.metrics.StarterLatencyMetricsDoc;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the data availability monitoring. Verifies that the starter can measure how
 * long it takes for a created process instance to become queryable via the search API.
 *
 * <p>This test relies on the {@link CamundaContainer}'s built-in H2 database and RDBMS exporter,
 * which flush immediately (PT0S), making process instances queryable almost instantly.
 *
 * <p>The starter runs for {@code duration-limit} seconds (blocking Spring Boot startup). During
 * that time, it creates instances and the {@link
 * io.camunda.zeebe.metrics.ProcessInstanceStartMeter} periodically checks if they are queryable. By
 * the time this test method executes, the data availability latency metric should have recordings.
 */
@Testcontainers
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      "load-tester.starter.rate=1",
      "load-tester.starter.duration-limit=30",
      "load-tester.starter.threads=1",
      "load-tester.starter.payload-path=bpmn/small_payload.json",
      "load-tester.monitor-data-availability=true",
      "load-tester.monitor-data-availability-interval=500ms",
      "load-tester.perform-read-benchmarks=false",
    })
@ActiveProfiles({"starter", "it"})
@Import(DataAvailabilityIT.RejectSearchesOnDefaultClient.class)
class DataAvailabilityIT {

  @Container
  static final CamundaContainer CAMUNDA = CamundaContainerProvider.createCamundaContainer();

  @Autowired private MeterRegistry meterRegistry;

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Test
  void shouldMeasureDataAvailabilityLatency() {
    // given - starter has run for duration-limit seconds with data availability monitoring, while
    // searches on the default client are rejected: only the separate query client can measure it

    // then - verify that data availability latency was recorded
    final var timer =
        meterRegistry.find(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName()).timer();
    assertThat(timer)
        .describedAs("Data availability latency timer should be registered")
        .isNotNull();
    assertThat(timer.count())
        .describedAs("At least one data availability measurement should be recorded")
        .isGreaterThan(0);
  }

  @TestConfiguration
  static class RejectSearchesOnDefaultClient {

    @Bean
    static BeanPostProcessor rejectSearches() {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(final Object bean, final String beanName) {
          if (!(bean instanceof final CamundaClient client)) {
            return bean;
          }
          final var spy = spy(client);
          doThrow(new IllegalStateException("searches must not use the default client"))
              .when(spy)
              .newProcessInstanceSearchRequest();
          return spy;
        }
      };
    }
  }
}
