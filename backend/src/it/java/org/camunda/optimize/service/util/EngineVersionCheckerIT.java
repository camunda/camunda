/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.rest.engine.EngineContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;

public class EngineVersionCheckerIT extends AbstractIT {

  @RegisterExtension
  protected final LogCapturer engineContextLogCapturer = LogCapturer.create()
    .forLevel(Level.ERROR)
    .captureForType(EngineContext.class);

  @Test
  public void engineVersionCantBeDetermined() {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration()
      .setRest("http://localhost:8080/engine-rest/ding-dong-you-rest-path-is-wrong");

    // when
    embeddedOptimizeExtension.reloadConfiguration();
    embeddedOptimizeExtension.authenticateUser("", "");

    engineContextLogCapturer.assertContains(
      "Failed to validate engine camunda-bpm version with error message: While checking the Engine version, " +
        "following error occurred: Status code: 404, this means you either configured a wrong endpoint or you have" +
        " an unsupported engine version < "
    );
  }

}
