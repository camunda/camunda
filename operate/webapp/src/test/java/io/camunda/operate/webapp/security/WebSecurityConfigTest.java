/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.config.operate.CloudProperties;
import io.camunda.config.operate.OperateProperties;
import io.camunda.config.operate.WebSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@ExtendWith(MockitoExtension.class)
public class WebSecurityConfigTest {

  private WebSecurityConfig underTest;
  @Mock private OperateProperties operateProperties;
  @Mock private CloudProperties cloudProperties;
  private WebSecurityProperties webSecurityProperties;
  @Mock private HttpSecurity http;

  @BeforeEach
  public void setUp() {
    webSecurityProperties = new WebSecurityProperties();
    when(operateProperties.getCloud()).thenReturn(cloudProperties);
    when(operateProperties.getWebSecurity()).thenReturn(webSecurityProperties);
    underTest = new WebSecurityConfig(operateProperties, mock(), mock(), mock());
  }

  @Test
  public void testSaasSCPHeadersDefault() throws Exception {
    when(cloudProperties.getClusterId()).thenReturn("Id");

    final String scpHeader = underTest.getContentSecurityPolicy();

    assertEquals(WebSecurityProperties.DEFAULT_SAAS_SECURITY_POLICY, scpHeader);
  }

  @Test
  public void testSaasSCPHeadersCustom() throws Exception {
    final String customPolicy = "custom";
    when(cloudProperties.getClusterId()).thenReturn("Id");
    webSecurityProperties.setContentSecurityPolicy(customPolicy);

    final String scpHeader = underTest.getContentSecurityPolicy();

    assertEquals(customPolicy, scpHeader);
  }

  @Test
  public void testSmCSPHeadersDefault() throws Exception {
    final String scpHeader = underTest.getContentSecurityPolicy();

    assertEquals(WebSecurityProperties.DEFAULT_SM_SECURITY_POLICY, scpHeader);
  }

  @Test
  public void testSmCSPHeadersCustom() throws Exception {
    final String customPolicy = "custom";
    webSecurityProperties.setContentSecurityPolicy(customPolicy);

    final String scpHeader = underTest.getContentSecurityPolicy();

    assertEquals(customPolicy, scpHeader);
  }
}
