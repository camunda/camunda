/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.StarterProperties;
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class StarterTest {

  @Test
  void shouldReportGaugeTagsFromProperties() {
    // given
    final var starterProperties = new StarterProperties();
    starterProperties.setProcessId("foobar");
    starterProperties.setThreads(2);
    final var properties = new LoadTesterProperties();
    properties.setStarter(starterProperties);

    final var registry = new SimpleMeterRegistry();

    // when
    new Starter(
        properties,
        registry,
        mock(StarterLivenessIndicator.class),
        mock(ProcessDeployer.class),
        mock(ProcessInstanceCreator.class),
        Optional.empty(),
        Optional.empty(),
        mock(ApplicationContext.class));

    // then
    final var gauge = registry.find(StarterMetricsDoc.CLIENT_INFO.getName()).gauge();

    assertThat(gauge).describedAs("client.info gauge should be registered").isNotNull();
    assertThat(gauge.value()).describedAs("client.info gauge value should be 1").isEqualTo(1.0);
    assertThat(gauge.getId().getTags())
        .describedAs("client.info gauge tags should reflect the starter configuration")
        .containsExactlyInAnyOrder(
            Tag.of(StarterMetricKeyNames.NAME.asString(), "starter"),
            Tag.of(StarterMetricKeyNames.PROCESS_ID.asString(), "foobar"),
            Tag.of(StarterMetricKeyNames.NB_THREADS.asString(), "2"));
  }
}
