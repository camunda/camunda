/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.secured.es;

public class ConnectToElasticsearchSslCustomCertAndCaIT extends AbstractConnectToElasticsearchIT {

  private static final String CONFIG_FILE = "secured-connection-ssl-custom-cert-and-ca.yaml";
  private static final String CONTEXT_FILE = "classpath:embeddedOptimizeContext-ssl-custom-cert-and-ca.xml";

  @Override
  protected String getCustomConfigFile() {
    return CONFIG_FILE;
  }

  @Override
  protected String getContextFile() {
    return CONTEXT_FILE;
  }

}
