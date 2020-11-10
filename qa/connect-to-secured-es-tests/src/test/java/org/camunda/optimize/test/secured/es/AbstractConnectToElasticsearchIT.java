/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.secured.es;

import org.camunda.optimize.service.metadata.PreviousVersion;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.upgrade.main.TestUpgradeProcedure;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractConnectToElasticsearchIT {

  @RegisterExtension
  public EmbeddedOptimizeExtension embeddedOptimizeExtension = getEmbeddedOptimizeExtension();

  protected abstract String getCustomConfigFile();

  protected abstract String getContextFile();

  @BeforeEach
  public void before() throws Exception {
    getEmbeddedOptimizeExtension().stopOptimize();
    getEmbeddedOptimizeExtension().startOptimize();
  }

  @Test
  public void connectToSecuredElasticsearch() {
    // given a license and a secured optimize -> es connection
    String license = FileReaderUtil.readValidTestLicense();

    // when doing a request to add the license to optimize
    Response response =
      embeddedOptimizeExtension.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .withoutAuthentication()
        .execute();

    // then Optimize should be able to successfully perform the underlying request to elasticsearch
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void runUpgradeAgainstSecuredElasticSearch() {
    // given an upgrade procedure against ES with custom configuration
    final TestUpgradeProcedure testUpgradeProcedure = new TestUpgradeProcedure(
      PreviousVersion.PREVIOUS_VERSION,
      Version.VERSION,
      getCustomConfigFile()
    );

    // when
    getEmbeddedOptimizeExtension().stopOptimize();
    // the metadata version needs to match the stated versionFrom for the upgrade to pass validation
    testUpgradeProcedure.setMetadataVersionInElasticSearch(PreviousVersion.PREVIOUS_VERSION);

    // then
    testUpgradeProcedure.performUpgrade();
  }

  private EmbeddedOptimizeExtension getEmbeddedOptimizeExtension() {
    return new EmbeddedOptimizeExtension(getContextFile());
  }

}
