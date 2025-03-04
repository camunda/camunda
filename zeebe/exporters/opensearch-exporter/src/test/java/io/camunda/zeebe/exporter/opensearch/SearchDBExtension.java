/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;

public abstract class SearchDBExtension
    implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

  protected static final String TEST_INTEGRATION_OPENSEARCH_AWS_URL =
      "test.integration.opensearch.aws.url";

  static SearchDBExtension create() {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      return new ContainerizedSearchDBExtension();
    } else {
      return new AWSSearchDBExtension(openSearchAwsInstanceUrl);
    }
  }

  abstract OpensearchExporterConfiguration config();

  abstract ProtocolFactory recordFactory();

  abstract TemplateReader templateReader();

  abstract RecordIndexRouter indexRouter();

  abstract BulkIndexRequest bulkRequest();

  abstract TestClient testClient();

  abstract OpensearchClient client();
}
