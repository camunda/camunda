/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin.importing.businesskey;

public interface BusinessKeyImportAdapter {
  /**
   * Adapts the business key a process instance to be imported.
   *
   * @param businessKey The businessKey that will be changed before importing to Optimize.
   * @return An adapted businessKey that is imported to Optimize.
   */
  String adaptBusinessKey(String businessKey);
}
