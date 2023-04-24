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
import picocli.CommandLine.Option;

public final class ClientMixin {
  @Option(
      names = "--address",
      description =
          "Specify a contact point address. If omitted, will read from the environment variable 'ZEEBE_ADDRESS'")
  private String address;

  @Option(
      names = "--audience",
      description =
          "Specify the resource that the access token should be valid for. If omitted, will read from the environment variable 'ZEEBE_TOKEN_AUDIENCE'")
  private String audience;

  @Option(
      names = "--authzUrl",
      description =
          "Specify an authorization server URL from which to request an access token. If omitted, will read from the environment variable 'ZEEBE_AUTHORIZATION_SERVER_URL'")
  private String authzUrl;

  @Option(
      names = "--certPath",
      description =
          "Specify a path to a certificate with which to validate gateway requests. If omitted, will read from the environment variable 'ZEEBE_CA_CERTIFICATE_PATH'")
  private String certPath;

  @Option(
      names = "--clientCache",
      description =
          "Specify the path to use for the OAuth credentials cache. If omitted, will read from the environment variable 'ZEEBE_CLIENT_CONFIG_PATH'")
  private String clientCache;

  @Option(
      names = "--clientId",
      description =
          "Specify a client identifier to request an access token. If omitted, will read from the environment variable 'ZEEBE_CLIENT_ID'")
  private String clientId;

  @Option(
      names = "--clientSecret",
      description =
          "Specify a client secret to request an access token. If omitted, will read from the environment variable 'ZEEBE_CLIENT_SECRET'")
  private String clientSecret;

  @Option(
      names = "--host",
      description =
          "Specify the host part of the gateway address. If omitted, will read from the environment variable 'ZEEBE_HOST'",
      defaultValue = "127.0.0.1")
  private String host;

  @Option(
      names = "--port",
      description =
          "Specify the port part of the gateway address. If omitted, will read from the environment variable 'ZEEBE_PORT'",
      defaultValue = "26500",
      type = Integer.class)
  private int port;

  @Option(
      names = "--insecure",
      description =
          "Specify if zbctl should use an unsecured connection. If omitted, will read from the environment variable 'ZEEBE_INSECURE_CONNECTION'",
      defaultValue = "false",
      type = Boolean.class)
  private boolean insecure;

  public ZeebeClient client() {
    var builder = ZeebeClient.newClientBuilder().gatewayAddress(address());

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

    return builder.build();
  }

  public String address() {
    if (address != null && !address.isBlank()) {
      return address;
    }

    return host + ":" + port;
  }
}
