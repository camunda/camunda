/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.INTEGRATION_TESTS;

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
  properties = {
    INTEGRATION_TESTS + "=true",
    // given
    // this config prevented Optimize from booting
    "server.compression.enabled=true"
  }
)
@DirtiesContext
@Disabled
public class SpringBootCompressionIT extends AbstractIT {

  @Test
  public void optimizeBootsWithEnabledCompression() {
    // see given on @SpringBootTest annotation
    // when/then Optimize boots successfully and requests are working
    embeddedOptimizeExtension.getRequestExecutor().buildCheckImportStatusRequest().execute().close();
  }

}
