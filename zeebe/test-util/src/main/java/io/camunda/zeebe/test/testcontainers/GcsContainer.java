/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;
import org.testcontainers.utility.DockerImageName;

public final class GcsContainer extends GenericContainer<GcsContainer> {
  private static final DockerImageName IMAGE = DockerImageName.parse("fsouza/fake-gcs-server");
  private static final String IMAGE_TAG = "1";
  private static final int PORT = 8000;

  public GcsContainer() {
    super(IMAGE.withTag(IMAGE_TAG));
    setCommand("-scheme", "http", "-port", String.valueOf(PORT));
    setExposedPorts(List.of(PORT));

    // for uploads to work, the container has to advertise the right address; since we don't know
    // in advance what that will be, we need to update it post-start. to be nice, we do that as part
    // of the wait strategy, so the container is deemed started when it's ready to be used
    // completely
    setWaitStrategy(
        new WaitAllStrategy()
            .withStartupTimeout(Duration.ofMinutes(1))
            .withStrategy(new HostPortWaitStrategy())
            .withStrategy(new URLUpdatingStrategy()));
  }

  public String externalEndpoint() {
    return "http://%s:%d".formatted(getHost(), getMappedPort(PORT));
  }

  private static final class URLUpdatingStrategy implements WaitStrategy {

    private Duration startupTimeout = Duration.ofSeconds(30);

    @Override
    public void waitUntilReady(final WaitStrategyTarget waitStrategyTarget) {
      final var endpoint =
          "http://" + waitStrategyTarget.getHost() + ":" + waitStrategyTarget.getMappedPort(PORT);
      Awaitility.await("until the external URL has been changed")
          .atMost(startupTimeout)
          .untilAsserted(() -> refreshExternalURL(endpoint));
    }

    @Override
    public WaitStrategy withStartupTimeout(final Duration startupTimeout) {
      this.startupTimeout = startupTimeout;
      return this;
    }

    private void refreshExternalURL(final String endpoint) throws Exception {
      final var modifyExternalUrlRequestUri = endpoint + "/_internal/config";
      final var updateExternalUrlJson = "{\"externalUrl\": \"" + endpoint + "\"}";
      final var req =
          HttpRequest.newBuilder()
              .uri(URI.create(modifyExternalUrlRequestUri))
              .header("Content-Type", "application/json")
              .PUT(BodyPublishers.ofString(updateExternalUrlJson))
              .build();
      final var response = HttpClient.newBuilder().build().send(req, BodyHandlers.discarding());

      if (response.statusCode() != 200) {
        throw new RuntimeException(
            "error updating fake-gcs-server with external url, response status code "
                + response.statusCode()
                + " != 200");
      }
    }
  }
}
