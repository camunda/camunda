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
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;

public abstract class SearchDBExtension
    implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {

  protected static final String IT_OPENSEARCH_AWS_INSTANCE_URL_PROPERTY =
      "camunda.it.opensearch.aws_instance_url";

  private static boolean isAwsTest;

  static SearchDBExtension create() {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(IT_OPENSEARCH_AWS_INSTANCE_URL_PROPERTY)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      return new ContainerizedSearchDBExtension();
    } else {
      isAwsTest = true;
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

  public boolean isAwsTest() {
    return isAwsTest;
  }
}
