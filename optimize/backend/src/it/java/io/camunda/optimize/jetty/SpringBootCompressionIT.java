/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.jetty;

import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;

import io.camunda.optimize.AbstractPlatformIT;
import java.util.Collections;
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
