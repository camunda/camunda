/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class ElasticsearchExporterConfigurationTest {

  @Test
  void shouldNotExposeUrlCredentialsInToString() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.url = "http://elastic:s3cr3t@localhost:9200";

    // when
    final var result = config.toString();

    // then
    assertThat(result).contains("localhost:9200").doesNotContain("s3cr3t");
  }
}
