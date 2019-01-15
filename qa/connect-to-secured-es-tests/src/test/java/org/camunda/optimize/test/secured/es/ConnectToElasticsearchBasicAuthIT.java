package org.camunda.optimize.test.secured.es;

import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;

public class ConnectToElasticsearchBasicAuthIT extends AbstractConnectToElasticsearchIT {
  @Override
  protected EmbeddedOptimizeRule getEmbeddedOptimizeRule() {
    return new EmbeddedOptimizeRule("classpath:embeddedOptimizeContext-basic-auth.xml");
  }
}
