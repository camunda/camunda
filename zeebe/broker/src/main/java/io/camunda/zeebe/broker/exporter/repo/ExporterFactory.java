/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.repo;

import io.camunda.zeebe.exporter.api.Exporter;

/**
 * When zeebe creates exporters, it first looks if there is an ExporterFactory in the spring context
 * for the exporter id. If yes, it uses newInstance on this factory. This makes it possible to pass
 * spring beans to the exporter during creation. If no, the old default behaviour is to simply
 * create the exporter by the provided className.
 */
public interface ExporterFactory {

  /**
   * @return The ID of the exporter, e.g. "rdbms" or "elasticsearch"
   */
  String exporterId();

  /**
   * @return Returns a new instance of the Exporter
   */
  Exporter newInstance() throws ExporterInstantiationException;
}
