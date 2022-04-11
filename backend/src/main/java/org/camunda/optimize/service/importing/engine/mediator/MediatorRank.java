/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

public enum MediatorRank {
  // the order of the entries determines the default mediator execution order
  TENANT,
  DEFINITION,
  DEFINITION_XML,
  INSTANCE,
  INSTANCE_SUB_ENTITIES,
  IMPORT_META_DATA,
  ;
}
