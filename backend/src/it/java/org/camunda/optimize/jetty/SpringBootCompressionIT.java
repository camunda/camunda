/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Collections;

@DirtiesContext
public class SpringBootCompressionIT extends AbstractIT {

  @Test
  public void optimizeBootsWithEnabledCompression() {
    startAndUseNewOptimizeInstance(Collections.singletonMap("server.compression.enabled", "true"));

    embeddedOptimizeExtension.getRequestExecutor().buildCheckImportStatusRequest().execute(200).close();
  }

}
