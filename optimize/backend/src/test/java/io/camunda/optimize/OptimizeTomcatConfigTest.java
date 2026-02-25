/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants;
import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
public class OptimizeTomcatConfigTest {
  @Captor ArgumentCaptor<TomcatConnectorCustomizer> connectorCustomizerCaptor;
  @Captor ArgumentCaptor<Connector> connectorCaptor;

  @Mock private Environment environment;

  @Mock private ConfigurationService configurationService;

  @Mock private TomcatServletWebServerFactory factory;
  @InjectMocks private OptimizeTomcatConfig optimizeTomcatConfig;

  @Test
  public void shouldAddConnectorsForHttpAndHttps() {
    // when
    when(environment.getProperty(anyString())).thenReturn("property");
    when(environment.getProperty(EnvironmentPropertiesConstants.HTTP_PORT_KEY)).thenReturn("8090");
    when(environment.getProperty(EnvironmentPropertiesConstants.HTTPS_PORT_KEY)).thenReturn("8091");

    when(configurationService.getContainerHost()).thenReturn("localhost");
    // then
    optimizeTomcatConfig.tomcatFactoryCustomizer().customize(factory);

    // verify HTTPS connector customizer is added with correct port
    verify(factory).addConnectorCustomizers(connectorCustomizerCaptor.capture());
    final Connector httpsConnector = new Connector();
    final TomcatConnectorCustomizer connectorCustomizer = connectorCustomizerCaptor.getValue();
    connectorCustomizer.customize(httpsConnector);
    assertThat(httpsConnector.getPort()).isEqualTo(8091);
    assertThat(httpsConnector.getScheme()).isEqualTo("https");
    assertThat(httpsConnector.getSecure()).isTrue();

    // verify HTTP connector is added with correct port
    verify(factory).addAdditionalConnectors(connectorCaptor.capture());
    final Connector httpConnector = connectorCaptor.getValue();
    assertThat(httpConnector.getPort()).isEqualTo(8090);
    assertThat(httpConnector.getScheme()).isEqualTo("http");
    assertThat(httpConnector.getSecure()).isFalse();
  }

  @Test
  public void shouldNotAddConnectorForEmptyHttpPort() {
    // when
    when(environment.getProperty(anyString())).thenReturn("property");
    when(environment.getProperty(EnvironmentPropertiesConstants.HTTP_PORT_KEY)).thenReturn("");
    when(environment.getProperty(EnvironmentPropertiesConstants.HTTPS_PORT_KEY)).thenReturn("8091");

    when(configurationService.getContainerHost()).thenReturn("localhost");
    // then
    optimizeTomcatConfig.tomcatFactoryCustomizer().customize(factory);

    // verify additional HTTP connector is not added
    verify(factory, never()).addAdditionalConnectors(any());
    // verify HTTPS connector customizer is added with correct port
    verify(factory).addConnectorCustomizers(connectorCustomizerCaptor.capture());
    final Connector httpsConnector = new Connector();
    final TomcatConnectorCustomizer connectorCustomizer = connectorCustomizerCaptor.getValue();
    connectorCustomizer.customize(httpsConnector);
    assertThat(httpsConnector.getPort()).isEqualTo(8091);
    assertThat(httpsConnector.getScheme()).isEqualTo("https");
    assertThat(httpsConnector.getSecure()).isTrue();
  }

  @Test
  public void shouldReturnNegativePortForNullHttpPort() {
    // when
    final String httpPortKey = EnvironmentPropertiesConstants.HTTP_PORT_KEY;
    when(environment.getProperty(httpPortKey)).thenReturn(null);
    // then
    assertThat(optimizeTomcatConfig.getPort(httpPortKey)).isEqualTo(-1);
  }

  @Test
  public void shouldReturnNegativePortForEmptyHttpPort() {
    // when
    final String httpPortKey = EnvironmentPropertiesConstants.HTTP_PORT_KEY;
    when(environment.getProperty(httpPortKey)).thenReturn("");
    // then
    assertThat(optimizeTomcatConfig.getPort(httpPortKey)).isEqualTo(-1);
  }
}
