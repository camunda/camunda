/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */


import org.camunda.optimize.data.generation.DataGenerationMain

class CamBpmDataGenerator {
  def static generate() {
    DataGenerationMain.main(new String[]{
      // hardcoded setup for now to have reproducable tests
      "--numberOfProcessInstances", "10",
      "--processDefinitions", "InvoiceDataFor2TenantsAndShared:1",
      "--numberOfDecisionInstances", "10",
      "--decisionDefinitions", "DecideDish:1"
    })
  }
}
