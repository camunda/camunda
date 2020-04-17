/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.importing.businesskey;

import java.util.List;

public interface BusinessKeyImportAdapter {
  /**
   * Adapts the business key of each process instance to be imported.
   *
   * @param processInstances The processInstances that would be imported by Optimize whose businessKeys to change.
   * @return An adapted list of process instances that is imported to Optimize.
   */
  List<PluginProcessInstanceDto> adaptBusinessKeys(List<PluginProcessInstanceDto> processInstances);
}
