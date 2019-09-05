/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.exporter.util;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collections;
import org.apache.http.HttpHost;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.security.PutRoleRequest;
import org.elasticsearch.client.security.PutUserRequest;
import org.elasticsearch.client.security.RefreshPolicy;
import org.elasticsearch.client.security.user.User;
import org.elasticsearch.client.security.user.privileges.Role;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.Base58;

public class ElasticsearchContainer extends GenericContainer<ElasticsearchContainer>
    implements ElasticsearchNode<ElasticsearchContainer> {
  private static final int DEFAULT_HTTP_PORT = 9200;
  private static final int DEFAULT_TCP_PORT = 9300;
  private static final String DEFAULT_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch";

  private boolean isSslEnabled;
  private boolean isAuthEnabled;
  private String username;
  private String password;
  private RestHighLevelClient client;
  private int port;

  public ElasticsearchContainer() {
    this(ElasticsearchClient.class.getPackage().getImplementationVersion());
  }

  public ElasticsearchContainer(final String version) {
    super(DEFAULT_IMAGE + ":" + version);
  }

  @Override
  public ElasticsearchContainer withXpack() {
    return withEnv("xpack.license.self_generated.type", "trial");
  }

  @Override
  public ElasticsearchContainer withUser(final String username, final String password) {
    this.username = username;
    this.password = password;
    this.isAuthEnabled = true;

    return withXpack()
        .withEnv("xpack.security.enabled", "true")
        .withEnv("xpack.security.authc.anonymous.username", "anon")
        .withEnv("xpack.security.authc.anonymous.roles", "superuser")
        .withEnv("xpack.security.authc.anonymous.authz_exception", "true");
  }

  @Override
  public ElasticsearchContainer withJavaOptions(final String... options) {
    return this;
  }

  @Override
  public ElasticsearchContainer withKeyStore(final String keyStore) {
    this.isSslEnabled = true;

    return withXpack()
        .withClasspathResourceMapping(
            keyStore, "/usr/share/elasticsearch/config/keystore.p12", BindMode.READ_WRITE)
        .withEnv("xpack.security.http.ssl.enabled", "true")
        .withEnv("xpack.security.http.ssl.keystore.path", "keystore.p12");
  }

  @Override
  public HttpHost getRestHttpHost() {
    final String scheme = isSslEnabled ? "https" : "http";
    final String host = getContainerIpAddress();
    final int port = this.port > 0 ? this.port : getMappedPort(DEFAULT_HTTP_PORT);

    return new HttpHost(host, port, scheme);
  }

  @Override
  public ElasticsearchContainer withPort(final int port) {
    this.port = port;
    addFixedExposedPort(port, DEFAULT_HTTP_PORT);
    return this;
  }

  @Override
  protected void doStart() {
    super.doStart();

    if (isAuthEnabled) {
      client = new RestHighLevelClient(RestClient.builder(getRestHttpHost()));
      setupUser();
    }
  }

  @Override
  public void stop() {
    super.stop();

    isAuthEnabled = false;
    isSslEnabled = false;
    username = null;
    password = null;

    if (client != null) {
      try {
        client.close();
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }

      client = null;
    }
  }

  @Override
  protected void configure() {
    final var waitStrategy =
        new HttpWaitStrategy()
            .forPort(DEFAULT_HTTP_PORT)
            .forStatusCodeMatching(status -> status == HTTP_OK || status == HTTP_UNAUTHORIZED);
    waitStrategy.withStartupTimeout(Duration.ofMinutes(2));
    if (isSslEnabled) {
      waitStrategy.usingTls();
    }

    withEnv("discovery.type", "single-node")
        .withEnv("cluster.name", "zeebe")
        .withEnv("ELASTIC_PASSWORD", "changeme")
        .withNetworkAliases("elasticsearch-" + Base58.randomString(6))
        .withEnv("discovery.type", "single-node");

    addExposedPorts(DEFAULT_HTTP_PORT, DEFAULT_TCP_PORT);
    setWaitStrategy(waitStrategy);
  }

  private void setupUser() {
    final User user = new User(username, Collections.singleton("zeebe-exporter"));

    try {
      createRole(client);
      client
          .security()
          .putUser(
              PutUserRequest.withPassword(
                  user, password.toCharArray(), true, RefreshPolicy.IMMEDIATE),
              RequestOptions.DEFAULT);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  // note: caveat, do not use custom index prefixes!
  private void createRole(final RestHighLevelClient client) throws IOException {
    final Role role = Role.builder().name("zeebe-exporter").build();

    client
        .security()
        .putRole(new PutRoleRequest(role, RefreshPolicy.IMMEDIATE), RequestOptions.DEFAULT);
  }
}
