/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os;

import java.util.Optional;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;

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
public abstract class SearchDBExtension implements BeforeAllCallback {
  public static final String TEST_INTEGRATION_OPENSEARCH_AWS_URL =
      "test.integration.opensearch.aws.url";

  public static SearchDBExtension create() {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      return new ContainerizedSearchDBExtension();
    } else {
      return new AWSSearchDBExtension(openSearchAwsInstanceUrl);
    }
  }

  /**
   * @return {@link OpenSearchClient} client that is configured against container or AWS OS instance
   *     depending on which extension was selected for execution.
   */
  public abstract OpenSearchClient osClient();

  /**
   * @return {@link OpenSearchAsyncClient} client that is configured against container or AWS OS
   *     instance depending on which extension was selected for execution.
   */
  public abstract OpenSearchAsyncClient asyncOsClient();

  /**
   * @return a URL for current instance of running OpenSearch.
   */
  public abstract String osUrl();
}
