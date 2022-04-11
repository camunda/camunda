/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;

import javax.ws.rs.core.Response;
import java.util.function.Supplier;

@AllArgsConstructor
public class LocalizationClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public JsonNode getLocalizationJson(String localeCode) {
    return getRequestExecutor()
      .buildGetLocalizationRequest(localeCode)
      .execute(JsonNode.class, Response.Status.OK.getStatusCode());
  }

  public String getLocalizedWhatsNewMarkdown(String localeCode) {
    return getRequestExecutor()
      .buildGetLocalizedWhatsNewMarkdownRequest(localeCode)
      .execute(String.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
