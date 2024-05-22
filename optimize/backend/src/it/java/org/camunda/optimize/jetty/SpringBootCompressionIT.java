/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;

import java.util.Collections;
import org.camunda.optimize.AbstractPlatformIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext
@Tag(OPENSEARCH_PASSING)
public class SpringBootCompressionIT extends AbstractPlatformIT {

  @Test
  public void optimizeBootsWithEnabledCompression() {
    startAndUseNewOptimizeInstance(Collections.singletonMap("server.compression.enabled", "true"));

    embeddedOptimizeExtension
        .getRequestExecutor()
        .buildCheckImportStatusRequest()
        .execute(200)
        .close();
  }
}
