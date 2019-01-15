package org.camunda.optimize.test.secured.es;

import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;

public class ConnectToElasticsearchBasicAuthSslCustomCertAndCaIT extends AbstractConnectToElasticsearchIT {
  @Override
  protected EmbeddedOptimizeRule getEmbeddedOptimizeRule() {
    return new EmbeddedOptimizeRule("classpath:embeddedOptimizeContext-basic-auth-ssl-custom-cert-and-ca.xml");
  }
}
