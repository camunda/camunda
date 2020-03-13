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
import io.zeebe.test.EmbeddedBrokerRule;

@Component("embeddedZeebeConfigurer")
@Profile("old-zeebe")
public class OldEmbeddedZeebeConfigurerImpl implements EmbeddedZeebeConfigurer {

  @Override
  public void injectPrefixToZeebeConfig(EmbeddedBrokerRule brokerRule, String exporterId, String prefix) {
    brokerRule.getBrokerCfg().getExporters().stream().filter(ec -> ec.getId().equals("elasticsearch")).forEach(ec -> {
      @SuppressWarnings("unchecked")
      final Map<String,String> indexArgs = (Map<String,String>) ec.getArgs().get("index");
      if (indexArgs != null) {
        indexArgs.put("prefix", prefix);
      } else {
        Assertions.fail("Unable to configure Elasticsearch exporter");
      }
    });
  }

}
