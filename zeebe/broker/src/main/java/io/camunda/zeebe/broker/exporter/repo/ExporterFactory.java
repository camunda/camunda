/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.repo;

import io.camunda.zeebe.exporter.api.Exporter;

public interface ExporterFactory {

  /**
   * @return The ID of the exporter, e.g. "rdbms" or "elasticsearch"
   */
  String exporterId();

  /**
   * @return Returns a new instance of the Exporter
   */
  Exporter newInstance() throws ExporterInstantiationException;

  /**
   * @return true if the other factory produces the same exporter type
   */
  boolean isSameTypeAs(ExporterFactory other);
}
