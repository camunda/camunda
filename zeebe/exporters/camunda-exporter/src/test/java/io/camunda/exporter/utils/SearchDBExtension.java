/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
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
public abstract class SearchDBExtension
    implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

  public static final String ENGINE_CLIENT_TEST_MARKERS =
      "engine" + RandomStringUtils.insecure().nextNumeric(10);

  public static final String INCIDENT_IDX_PREFIX =
      "incident" + RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();

  public static final String BATCH_IDX_PREFIX =
      "batch" + RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();

  public static final String ARCHIVER_IDX_PREFIX =
      "archiver" + RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();

  public static final String ZEEBE_IDX_PREFIX =
      "zeebeidx" + RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();

  public static final String CUSTOM_PREFIX =
      "custom" + RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();

  public static final String IDX_PROCESS_PREFIX =
      "idxtestprocess" + RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();

  public static final ProcessIndex PROCESS_INDEX = new ProcessIndex(IDX_PROCESS_PREFIX, true);

  public static final String IDX_FORM_PREFIX =
      "idxtestform" + RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();

  public static final FormIndex FORM_INDEX = new FormIndex(IDX_FORM_PREFIX, true);

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
   * @return context {@link ObjectMapper}
   */
  public abstract ObjectMapper objectMapper();

  /**
   * @return {@link ElasticsearchClient} client that is configured against a container
   */
  public abstract ElasticsearchClient esClient();

  /**
   * @return {@link OpenSearchClient} client that is configured against container or AWS OS instance
   *     depending on which extension was selected for execution.
   */
  public abstract OpenSearchClient osClient();

  /**
   * @return context ElasticSearch URL
   */
  public abstract String esUrl();

  /**
   * @return context OpenSearch URL
   */
  public abstract String osUrl();
}
