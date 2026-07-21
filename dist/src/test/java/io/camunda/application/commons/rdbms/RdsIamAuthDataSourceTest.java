/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Aws;
import io.camunda.configuration.Rdbms;
import org.junit.jupiter.api.Test;

class RdsIamAuthDataSourceTest {

  private static Rdbms rdbmsWithUrl(final String url) {
    final var rdbms = new Rdbms();
    rdbms.setUrl(url);
    rdbms.setUsername("app-user");
    rdbms.setAwsEnabled(true);
    return rdbms;
  }

  private static Aws staticAws() {
    final var aws = new Aws();
    aws.setAccessKey("key");
    aws.setSecretKey("secret");
    aws.setRegion("eu-west-1");
    return aws;
  }

  @Test
  void shouldGenerateSignedTokenForConfiguredIdentity() {
    // given
    final var dataSource =
        RdsIamAuthDataSource.of(
            rdbmsWithUrl("jdbc:postgresql://db.example.com:5432/camunda"), staticAws());

    // when
    final var token = dataSource.generateToken();

    // then the token is a presigned request for the configured endpoint, user, and identity
    assertThat(token).startsWith("db.example.com:5432/");
    assertThat(token).contains("DBUser=app-user");
    assertThat(token).contains("X-Amz-Credential=key");
    assertThat(token).contains("X-Amz-Signature=");
  }

  @Test
  void shouldDefaultPortByVendorWhenUrlOmitsIt() {
    // given
    final var dataSource =
        RdsIamAuthDataSource.of(
            rdbmsWithUrl("jdbc:postgresql://db.example.com/camunda"), staticAws());

    // when / then
    assertThat(dataSource.generateToken()).startsWith("db.example.com:5432/");
  }

  @Test
  void shouldRejectUrlWithoutHostname() {
    assertThatThrownBy(() -> RdsIamAuthDataSource.of(rdbmsWithUrl("jdbc:h2:mem:test"), staticAws()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jdbc:h2:mem:test");
  }

  @Test
  void shouldRejectUrlWithoutPortForVendorWithoutDefault() {
    assertThatThrownBy(
            () ->
                RdsIamAuthDataSource.of(
                    rdbmsWithUrl("jdbc:sqlserver://db.example.com/camunda"), staticAws()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("explicit port");
  }
}
