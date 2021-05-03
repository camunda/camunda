/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.util;

import org.apache.http.HttpHost;

public interface ElasticsearchNode<SELF extends ElasticsearchNode> {

  void start();

  void stop();

  SELF withXpack();

  SELF withUser(String username, String password);

  SELF withJavaOptions(String... options);

  SELF withKeyStore(String keyStore);

  HttpHost getRestHttpHost();

  SELF withPort(int port);
}
