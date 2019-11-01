/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.OptimizeVersionDto;
import org.camunda.optimize.service.metadata.Version;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MetadataRestServiceIT extends AbstractIT {

  @Test
  public void getOptimizeVersion() {
    // when
    OptimizeVersionDto optimizeVersionDto =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetOptimizeVersionRequest()
        .execute(OptimizeVersionDto.class, 200);

    // then
    assertThat(optimizeVersionDto.getOptimizeVersion(), is(Version.RAW_VERSION));
  }
}
