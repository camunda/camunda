package org.camunda.optimize.test.secured.es;

import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;

public class ConnectToElasticsearchSslCustomCertAndCaIT extends AbstractConnectToElasticsearchIT {
  @Override
  protected EmbeddedOptimizeRule getEmbeddedOptimizeRule() {
    return new EmbeddedOptimizeRule("classpath:embeddedOptimizeContext-ssl-custom-cert-and-ca.xml");
  }
}
