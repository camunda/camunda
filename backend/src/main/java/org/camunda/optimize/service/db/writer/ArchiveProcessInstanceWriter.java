/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;

import java.util.Set;

public interface ArchiveProcessInstanceWriter extends ConfigurationReloadable {

  void createInstanceIndicesIfMissing(final Set<String> processDefinitionKeys);

}
