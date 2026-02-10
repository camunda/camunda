/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredTenant;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.TenantValidator;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.util.Either;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TenantConfigurerTest {

  private static final ConfiguredTenant MISSING_TENANT_ID =
      new ConfiguredTenant(
          null,
          "Foo",
          "",
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList());
  private static final ConfiguredTenant VALID_TENANT =
      new ConfiguredTenant(
          "foo",
          "Foo",
          "",
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList());

  private static final TenantValidator VALIDATOR =
      new TenantValidator(new IdentifierValidator(Pattern.compile(".*")));
  private static final TenantConfigurer CONFIGURER = new TenantConfigurer(VALIDATOR);

  @Test
  void shouldReturnViolationOnValidationFailure() {
    // when:
    final Either<List<String>, TenantRecord> result = CONFIGURER.configure(MISSING_TENANT_ID);

    // then:
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).contains("No tenantId provided");
  }

  @Test
  void shouldSuccessfullyConfigure() {
    // when:
    final Either<List<String>, TenantRecord> result = CONFIGURER.configure(VALID_TENANT);

    // then:
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldAggregateToViolations() {
    // given:
    final List<ConfiguredTenant> tenants =
        List.of(VALID_TENANT, MISSING_TENANT_ID, MISSING_TENANT_ID);

    // when:
    final Either<List<String>, List<TenantRecord>> result = CONFIGURER.configureEntities(tenants);

    // then:
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).contains("No tenantId provided");
    assertThat(result.getLeft()).hasSize(2);
  }

  @Test
  void shouldAggregateToTenantRecords() {
    // given:
    final List<ConfiguredTenant> tenants = List.of(VALID_TENANT, VALID_TENANT);

    // when:
    final Either<List<String>, List<TenantRecord>> result = CONFIGURER.configureEntities(tenants);

    // then:
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).hasSize(2);
  }
}
