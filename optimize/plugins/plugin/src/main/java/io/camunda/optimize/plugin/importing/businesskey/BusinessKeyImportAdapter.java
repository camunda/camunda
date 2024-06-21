/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.plugin.importing.businesskey;

public interface BusinessKeyImportAdapter {

  /**
   * Adapts the business key a process instance to be imported.
   *
   * @param businessKey The businessKey that will be changed before importing to Optimize.
   * @return An adapted businessKey that is imported to Optimize.
   */
  String adaptBusinessKey(String businessKey);
}
