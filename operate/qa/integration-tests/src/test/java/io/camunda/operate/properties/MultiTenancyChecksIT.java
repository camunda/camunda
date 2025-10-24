/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.security.configuration.SecurityConfiguration;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(Enclosed.class)
public class MultiTenancyChecksIT {

  @RunWith(SpringRunner.class)
  @TestPropertySource(properties = {"camunda.security.multiTenancy.checksEnabled=true"})
  public static class EnabledCamelCaseIT extends BaseIT {

    @Test
    public void shouldEnableChecksWithCamelCaseProperty() {
      assertThat(securityConfiguration.getMultiTenancy().isChecksEnabled()).isEqualTo(true);
    }
  }

  @RunWith(SpringRunner.class)
  @TestPropertySource(properties = {"camunda.security.multiTenancy.checksEnabled=false"})
  public static class DisabledCamelCaseIT extends BaseIT {

    @Test
    public void shouldDisableChecksWithCamelCaseProperty() {
      assertThat(securityConfiguration.getMultiTenancy().isChecksEnabled()).isEqualTo(false);
    }
  }

  @RunWith(SpringRunner.class)
  @TestPropertySource(properties = {"camunda.security.multi-tenancy.checks-enabled=true"})
  public static class EnabledKebabCaseIT extends BaseIT {

    @Test
    public void shouldEnableChecksWithKebabCaseProperty() {
      assertThat(securityConfiguration.getMultiTenancy().isChecksEnabled()).isEqualTo(true);
    }
  }

  @RunWith(SpringRunner.class)
  @TestPropertySource(properties = {"camunda.security.multi-tenancy.checks-enabled=false"})
  public static class DisabledKebabCaseIT extends BaseIT {

    @Test
    public void shouldDisableChecksWithKebabCaseProperty() {
      assertThat(securityConfiguration.getMultiTenancy().isChecksEnabled()).isEqualTo(false);
    }
  }

  @SpringBootTest(
      classes = {
        CamundaSecurityProperties.class,
        UnifiedConfiguration.class,
        UnifiedConfigurationHelper.class
      },
      webEnvironment = SpringBootTest.WebEnvironment.NONE)
  public abstract static class BaseIT {
    @Autowired protected SecurityConfiguration securityConfiguration;
  }
}
