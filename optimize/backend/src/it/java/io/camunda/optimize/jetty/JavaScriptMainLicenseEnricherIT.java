/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.jetty;

import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static io.camunda.optimize.jetty.JavaScriptMainLicenseEnricherFilter.LICENSE_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractPlatformIT;
import io.camunda.optimize.util.FileReaderUtil;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class JavaScriptMainLicenseEnricherIT extends AbstractPlatformIT {
  private static final String MOCKED_JS_CONTENT = "/* no content */\n";

  @Test
  public void licenseIsAdded() {
    // when
    Response response =
        embeddedOptimizeExtension.rootTarget("/static/js/main.mock.chunk.js").request().get();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String body = response.readEntity(String.class);
    assertThat(body).isEqualTo(FileReaderUtil.readFile("/" + LICENSE_PATH) + MOCKED_JS_CONTENT);
  }
}
