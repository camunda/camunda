/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import java.util.Map;
import org.assertj.core.api.Assertions;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.test.EmbeddedBrokerRule;

@Component("embeddedZeebeCofigurer")
public class EmbeddedZeebeCofigurerImpl implements EmbeddedZeebeConfigurer {

  public void injectPrefixToZeebeConfig(EmbeddedBrokerRule brokerRule, String exporterId, String prefix) {
    final ExporterCfg exporterCfg = brokerRule.getBrokerCfg().getExporters().get(exporterId);
    final Map<String, String> indexArgs = (Map<String, String>) exporterCfg.getArgs().get("index");
    if (indexArgs != null) {
      indexArgs.put("prefix", prefix);
    } else {
      Assertions.fail("Unable to configure Elasticsearch exporter");
    }

  }

}
