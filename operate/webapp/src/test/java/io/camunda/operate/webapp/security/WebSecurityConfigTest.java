/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.operate.webapp.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.property.CloudProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.WebSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@ExtendWith(MockitoExtension.class)
public class WebSecurityConfigTest {

  @Mock private OperateProperties operateProperties;
  @InjectMocks private WebSecurityConfig underTest;
  private CloudProperties cloudProperties;
  private WebSecurityProperties webSecurityProperties;
  private HttpSecurity http;

  @BeforeEach
  public void setUp() {
    cloudProperties = mock(CloudProperties.class);
    webSecurityProperties = mock(WebSecurityProperties.class);
    http = mock(HttpSecurity.class);
    when(operateProperties.getCloud()).thenReturn(cloudProperties);
    when(operateProperties.getWebSecurity()).thenReturn(webSecurityProperties);
  }

  @Test
  public void testSaasSCPHeaders() throws Exception {
    when(cloudProperties.getClusterId()).thenReturn("Id");
    underTest.applySecurityHeadersSettings(http);

    verify(webSecurityProperties, times(0)).setContentSecurityPolicy(anyString());
  }

  @Test
  public void testSMSCPHeaders() throws Exception {
    when(cloudProperties.getClusterId()).thenReturn(null);
    underTest.applySecurityHeadersSettings(http);

    verify(webSecurityProperties, times(1)).setContentSecurityPolicy(anyString());
  }
}
