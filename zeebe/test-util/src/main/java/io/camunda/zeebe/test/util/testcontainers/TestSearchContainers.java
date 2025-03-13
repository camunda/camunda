/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.testcontainers;

import java.time.Duration;
import java.util.Objects;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.containers.BindMode;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("resource")
public final class TestSearchContainers {
  private static final DockerImageName ELASTIC_IMAGE =
      DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
          .withTag(
              Objects.requireNonNullElse(
                  org.elasticsearch.client.RestClient.class.getPackage().getImplementationVersion(),
                  "8.16.4"));

  private static final DockerImageName OPENSEARCH_IMAGE =
      DockerImageName.parse("opensearchproject/opensearch")
          .withTag(
              Objects.requireNonNullElse(
                  org.opensearch.client.RestClient.class.getPackage().getImplementationVersion(),
                  "2.14.0"));

  private TestSearchContainers() {}

  /**
   * Returns an OpenSearch container pointing at the same version as the {@link
   * org.opensearch.client.RestClient}.
   *
   * <p>The container is configured to use 512m of heap and 512m of direct memory. This is required
   * because OpenSearch, by default, will grab all the RAM available otherwise.
   *
   * <p>Additionally, security is explicitly disabled to avoid having tons of warning printed out.
   */
  public static OpensearchContainer<?> createDefaultOpensearchContainer() {
    return new OpensearchContainer<>(OPENSEARCH_IMAGE)
        .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m -XX:MaxDirectMemorySize=536870912")
        .withEnv("action.auto_create_index", "true");
  }

  /**
   * Returns an Elasticsearch container pointing at the same version as the {@link
   * org.elasticsearch.client.RestClient}.
   *
   * <p>The container is configured to use 512m of heap and 512m of direct memory. This is required
   * because Elasticsearch 7.x, by default, will grab all the RAM available otherwise.
   *
   * <p>Additionally, security is explicitly disabled to avoid having tons of warning printed out.
   */
  public static ElasticsearchContainer createDefeaultElasticsearchContainer() {
    return new ElasticsearchContainer(ELASTIC_IMAGE)
        // use JVM option files to avoid overwriting default options set by the ES container class
        .withClasspathResourceMapping(
            "elasticsearch-fast-startup.options",
            "/usr/share/elasticsearch/config/jvm.options.d/ elasticsearch-fast-startup.options",
            BindMode.READ_ONLY)
        // can be slow in CI
        .withStartupTimeout(Duration.ofMinutes(5))
        .withEnv("action.auto_create_index", "true")
        .withEnv("xpack.security.enabled", "false")
        .withEnv("xpack.watcher.enabled", "false")
        .withEnv("xpack.ml.enabled", "false")
        .withEnv("action.destructive_requires_name", "false");
  }
}
