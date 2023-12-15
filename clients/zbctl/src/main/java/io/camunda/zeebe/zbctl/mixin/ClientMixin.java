/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.mixin;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.zbctl.converters.DurationConverter;
import java.time.Duration;
import java.util.function.UnaryOperator;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

public final class ClientMixin {
  @Option(
      names = "--address",
      description =
          "Specify a contact point address. If omitted, will read from the environment variable 'ZEEBE_ADDRESS'",
      scope = ScopeType.INHERIT)
  private String address;

  @Option(
      names = "--audience",
      description =
          "Specify the resource that the access token should be valid for. If omitted, will read from the environment variable 'ZEEBE_TOKEN_AUDIENCE'",
      scope = ScopeType.INHERIT)
  private String audience;

  @Option(
      names = "--authzUrl",
      description =
          "Specify an authorization server URL from which to request an access token. If omitted, will read from the environment variable 'ZEEBE_AUTHORIZATION_SERVER_URL'",
      scope = ScopeType.INHERIT)
  private String authzUrl;

  @Option(
      names = "--certPath",
      description =
          "Specify a path to a certificate with which to validate gateway requests. If omitted, will read from the environment variable 'ZEEBE_CA_CERTIFICATE_PATH'",
      scope = ScopeType.INHERIT)
  private String certPath;

  @Option(
      names = "--clientCache",
      description =
          "Specify the path to use for the OAuth credentials cache. If omitted, will read from the environment variable 'ZEEBE_CLIENT_CONFIG_PATH'",
      scope = ScopeType.INHERIT)
  private String clientCache;

  @Option(
      names = "--clientId",
      description =
          "Specify a client identifier to request an access token. If omitted, will read from the environment variable 'ZEEBE_CLIENT_ID'",
      scope = ScopeType.INHERIT)
  private String clientId;

  @Option(
      names = "--clientSecret",
      description =
          "Specify a client secret to request an access token. If omitted, will read from the environment variable 'ZEEBE_CLIENT_SECRET'",
      scope = ScopeType.INHERIT)
  private String clientSecret;

  @Option(
      names = "--host",
      description =
          "Specify the host part of the gateway address. If omitted, will read from the environment variable 'ZEEBE_HOST'",
      defaultValue = "127.0.0.1",
      scope = ScopeType.INHERIT)
  private String host;

  @Option(
      names = "--port",
      description =
          "Specify the port part of the gateway address. If omitted, will read from the environment variable 'ZEEBE_PORT'",
      defaultValue = "26500",
      type = Integer.class,
      scope = ScopeType.INHERIT)
  private int port;

  @Option(
      names = "--insecure",
      description =
          "Specify if zbctl should use an unsecured connection. If omitted, will read from the environment variable 'ZEEBE_INSECURE_CONNECTION'",
      defaultValue = "false",
      type = Boolean.class,
      scope = ScopeType.INHERIT)
  private boolean insecure;

  @Option(
      names = "--requestTimeout",
      description = "Specify a default request timeout for all commands.",
      defaultValue = "30s",
      scope = ScopeType.INHERIT,
      converter = DurationConverter.class)
  private Duration requestTimeout;

  public ZeebeClient client() {
    return client(UnaryOperator.identity());
  }

  public ZeebeClient client(final UnaryOperator<ZeebeClientBuilder> configurator) {
    var builder =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(address())
            .defaultRequestTimeout(requestTimeout);

    if (insecure) {
      builder = builder.usePlaintext();
    }

    if (clientId != null
        || clientSecret != null
        || clientCache != null
        || audience != null
        || authzUrl != null) {
      var credentialsProvider =
          CredentialsProvider.newCredentialsProviderBuilder()
              .audience(audience)
              .authorizationServerUrl(authzUrl);

      if (clientId != null) {
        credentialsProvider = credentialsProvider.clientId(clientId);
      }

      if (clientCache != null) {
        credentialsProvider = credentialsProvider.credentialsCachePath(clientCache);
      }

      if (clientSecret != null) {
        credentialsProvider = credentialsProvider.clientSecret(clientSecret);
      }

      builder = builder.credentialsProvider(credentialsProvider.build());
    }

    return configurator.apply(builder).build();
  }

  public String address() {
    if (address != null && !address.isBlank()) {
      return address;
    }

    return host + ":" + port;
  }
}
