/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("fillPostImporterQueue")
public class FillPostImporterQueueStep extends AbstractStep implements DataInitializerStep {

  @Override
  public String toString() {
    return "FillPostImporterQueueStep{} " + super.toString();
  }
}
