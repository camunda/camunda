/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.templates;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.schema.migration.SemanticVersion;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import org.junit.jupiter.api.Test;

class OperationTemplateTest {

  @Test
  void templateShouldHaveCompletedDateFieldAndIncreasedMinorVersion() {
    assertThat(OperationTemplate.COMPLETED_DATE).isNotNull();
    assertThat(
        new SemanticVersion(new OperationTemplate("", false).getVersion()).isNewerThan("8.4.0"));
  }
}
