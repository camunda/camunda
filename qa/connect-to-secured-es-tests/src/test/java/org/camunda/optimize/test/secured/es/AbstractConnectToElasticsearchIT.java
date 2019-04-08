/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.secured.es;

import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractConnectToElasticsearchIT {

  private EmbeddedOptimizeRule embeddedOptimizeRule = getEmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(embeddedOptimizeRule);

  protected abstract EmbeddedOptimizeRule getEmbeddedOptimizeRule();

  @Before
  public void before() throws Exception {
    getEmbeddedOptimizeRule().stopOptimize();
    getEmbeddedOptimizeRule().startOptimize();
  }

  @Test
  public void connectToSecuredElasticsearch() throws IOException, URISyntaxException {
    // given a license and a secured optimize -> es connection
    String license = readFileToString("/license/ValidTestLicense.txt");

    // when doing a request to add the license to optimize
    Response response =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .withoutAuthentication()
        .execute();

    // then Optimize should be able to successfully perform the underlying request to elasticsearch
    assertThat(response.getStatus(), is(200));
  }

  private String readFileToString(String filePath) throws IOException, URISyntaxException {
    return new String(Files.readAllBytes(Paths.get(getClass().getResource(filePath).toURI())), StandardCharsets.UTF_8);
  }



}
