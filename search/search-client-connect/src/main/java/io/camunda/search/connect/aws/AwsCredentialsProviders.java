/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.aws;

import io.camunda.search.connect.configuration.AwsConfiguration;
import java.nio.file.Paths;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsWebIdentityTokenFileCredentialsProvider;

/** Builds AWS SDK credential/region providers from an {@link AwsConfiguration}. */
public final class AwsCredentialsProviders {

  private AwsCredentialsProviders() {}

  /**
   * Returns a provider for the configured identity: static credentials, web identity (IRSA), or —
   * when the configuration is empty — the SDK default provider chain (the previous behavior).
   */
  public static AwsCredentialsProvider from(final AwsConfiguration aws) {
    if (aws == null) {
      return DefaultCredentialsProvider.builder().build();
    }
    if (aws.getAccessKey() != null ^ aws.getSecretKey() != null) {
      throw new IllegalArgumentException(
          "AWS static credentials are incomplete: both accessKey and secretKey must be set");
    }
    if (aws.getRoleArn() != null ^ aws.getWebIdentityTokenFile() != null) {
      throw new IllegalArgumentException(
          "AWS web identity is incomplete: both roleArn and webIdentityTokenFile must be set");
    }
    final boolean hasStaticCredentials = aws.getAccessKey() != null;
    final boolean hasWebIdentity = aws.getRoleArn() != null;
    if (hasStaticCredentials && hasWebIdentity) {
      throw new IllegalArgumentException(
          "AWS configuration declares both static credentials and web identity; set only one");
    }
    if (hasStaticCredentials) {
      return StaticCredentialsProvider.create(
          aws.getSessionToken() != null
              ? AwsSessionCredentials.create(
                  aws.getAccessKey(), aws.getSecretKey(), aws.getSessionToken())
              : AwsBasicCredentials.create(aws.getAccessKey(), aws.getSecretKey()));
    }
    if (hasWebIdentity) {
      if (aws.getRegion() != null) {
        return StsWebIdentityTokenFileCredentialsProvider.builder()
            .stsClient(StsClient.builder().region(Region.of(aws.getRegion())).build())
            .roleArn(aws.getRoleArn())
            .webIdentityTokenFile(Paths.get(aws.getWebIdentityTokenFile()))
            .build();
      }
      return WebIdentityTokenFileCredentialsProvider.builder()
          .roleArn(aws.getRoleArn())
          .webIdentityTokenFile(Paths.get(aws.getWebIdentityTokenFile()))
          .build();
    }
    return DefaultCredentialsProvider.builder().build();
  }

  /** Returns the configured region, or the SDK default region provider chain's region. */
  public static Region region(final AwsConfiguration aws) {
    if (aws != null && aws.getRegion() != null) {
      return Region.of(aws.getRegion());
    }
    return new DefaultAwsRegionProviderChain().getRegion();
  }
}
