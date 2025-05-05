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

/**
 * {@code @SearchDBExtension} is an extension factory for concrete extensions: {@link
 * ContainerizedSearchDBExtension} and {@link AWSSearchDBExtension}.
 *
 * <p>Which extension will be created is controlled by the property
 * `test.integration.opensearch.aws.url`. If the mentioned property is defined then tests assume
 * that URL is correct and points to the AWS OS instance.
 *
 * <p>A user can pass property locally via `mvn -D test.integration.opensearch.aws.url=$AWS_OS_URL
 *
 * <p>If concrete extension not selected, the {@link ContainerizedSearchDBExtension} as selected by
 * default.
 */
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

  /**
   * @return context configuration {@link OpensearchExporterConfiguration}
   */
  abstract OpensearchExporterConfiguration config();

  /**
   * @return context {@link ProtocolFactory}
   */
  abstract ProtocolFactory recordFactory();

  /**
   * @return context {@link TemplateReader}
   */
  abstract TemplateReader templateReader();

  /**
   * @return context {@link RecordIndexRouter}
   */
  abstract RecordIndexRouter indexRouter();

  /**
   * @return context {@link BulkIndexRequest}
   */
  abstract BulkIndexRequest bulkRequest();

  /**
   * @return test thin OpenSearch client simplifies certain test procedures.
   * @see TestClient
   */
  abstract TestClient testClient();

  /**
   * @return configured {@link OpensearchClient}
   */
  abstract OpensearchClient client();
}
