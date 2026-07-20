/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.connect.configuration.AwsConfiguration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.regions.Region;

class AwsCredentialsProvidersTest {

  @Test
  void shouldResolveStaticCredentials() {
    // given
    final var aws = new AwsConfiguration();
    aws.setAccessKey("key");
    aws.setSecretKey("secret");

    // when
    final AwsCredentials credentials = AwsCredentialsProviders.from(aws).resolveCredentials();

    // then
    assertThat(credentials.accessKeyId()).isEqualTo("key");
    assertThat(credentials.secretAccessKey()).isEqualTo("secret");
  }

  @Test
  void shouldResolveSessionCredentialsWhenTokenIsSet() {
    // given
    final var aws = new AwsConfiguration();
    aws.setAccessKey("key");
    aws.setSecretKey("secret");
    aws.setSessionToken("token");

    // when
    final AwsCredentials credentials = AwsCredentialsProviders.from(aws).resolveCredentials();

    // then
    assertThat(credentials)
        .isInstanceOfSatisfying(
            AwsSessionCredentials.class,
            session -> assertThat(session.sessionToken()).isEqualTo("token"));
  }

  @Test
  void shouldBuildWebIdentityProvider() {
    // given
    final var aws = new AwsConfiguration();
    aws.setRoleArn("arn:aws:iam::111:role/some-role");
    aws.setWebIdentityTokenFile("/var/run/secrets/token");

    // when / then
    assertThat(AwsCredentialsProviders.from(aws))
        .isInstanceOf(WebIdentityTokenFileCredentialsProvider.class);
  }

  @Test
  void shouldFallBackToDefaultChainWhenEmpty() {
    // when / then
    assertThat(AwsCredentialsProviders.from(new AwsConfiguration()))
        .isInstanceOf(DefaultCredentialsProvider.class);
    assertThat(AwsCredentialsProviders.from(null)).isInstanceOf(DefaultCredentialsProvider.class);
  }

  @Test
  void shouldRejectPartialStaticCredentials() {
    // given
    final var aws = new AwsConfiguration();
    aws.setAccessKey("key-only");

    // when / then
    assertThatThrownBy(() -> AwsCredentialsProviders.from(aws))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("secretKey");
  }

  @Test
  void shouldRejectPartialWebIdentity() {
    // given
    final var aws = new AwsConfiguration();
    aws.setWebIdentityTokenFile("/var/run/secrets/token");

    // when / then
    assertThatThrownBy(() -> AwsCredentialsProviders.from(aws))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleArn");
  }

  @Test
  void shouldRejectBothStaticCredentialsAndWebIdentity() {
    // given
    final var aws = new AwsConfiguration();
    aws.setAccessKey("key");
    aws.setSecretKey("secret");
    aws.setRoleArn("arn:aws:iam::111:role/some-role");
    aws.setWebIdentityTokenFile("/var/run/secrets/token");

    // when / then
    assertThatThrownBy(() -> AwsCredentialsProviders.from(aws))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only one");
  }

  @Test
  void shouldPreferConfiguredRegion() {
    // given
    final var aws = new AwsConfiguration();
    aws.setRegion("eu-west-1");

    // when / then
    assertThat(AwsCredentialsProviders.region(aws)).isEqualTo(Region.EU_WEST_1);
  }
}
