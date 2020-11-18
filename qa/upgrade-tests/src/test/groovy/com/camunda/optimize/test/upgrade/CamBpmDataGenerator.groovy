/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package com.camunda.optimize.test.upgrade


import com.google.common.collect.ImmutableMap
import org.camunda.optimize.data.generation.DataGenerationMain
import org.camunda.optimize.data.generation.generators.dto.DataGenerationInformation
import org.camunda.optimize.data.generation.generators.impl.decision.DecideDishDataGenerator
import org.camunda.optimize.data.generation.generators.impl.process.InvoiceDataFor2TenantsAndSharedDataGenerator

class CamBpmDataGenerator {
  def static generate() {
    new DataGenerationMain(
      DataGenerationInformation.builder()
        .processInstanceCountToGenerate(10)
        .decisionInstanceCountToGenerate(10)
        .processDefinitionsAndNumberOfVersions(ImmutableMap.of(InvoiceDataFor2TenantsAndSharedDataGenerator.class.getSimpleName(), 1))
        .decisionDefinitionsAndNumberOfVersions(ImmutableMap.of(DecideDishDataGenerator.class.getSimpleName(), 1))
        .removeDeployments(true)
        .build()
    ).generateData();
  }
}
