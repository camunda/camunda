/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.secured.es;

import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;

public class ConnectToElasticsearchBasicAuthSslCustomCaIT extends AbstractConnectToElasticsearchIT {
  @Override
  protected EmbeddedOptimizeExtension getEmbeddedOptimizeExtension() {
    return new EmbeddedOptimizeExtension("classpath:embeddedOptimizeContext-basic-auth-ssl-custom-ca.xml");
  }
}
