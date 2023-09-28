/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.license.os;

import jakarta.ws.rs.NotSupportedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.license.LicenseManager;
import org.camunda.optimize.service.os.OptimizeOpensearchClient;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class LicenseManagerOS extends LicenseManager {
  private final OptimizeOpensearchClient osClient;

  @Override
  protected String retrieveStoredOptimizeLicense() {
    log.debug("Retrieving stored optimize license!");
    // TODO Will be implemented with OPT-7229
    throw new NotSupportedException("functionality not yet supported");
  }

  @Override
  public void storeLicense(String licenseAsString)
  {
    // TODO Will be implemented with OPT-7229
    throw new NotSupportedException("functionality not yet supported");
  }

}
