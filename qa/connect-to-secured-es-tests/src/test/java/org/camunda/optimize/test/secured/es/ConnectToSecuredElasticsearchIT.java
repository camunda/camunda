package org.camunda.optimize.test.secured.es;

import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ConnectToSecuredElasticsearchIT {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private EmbeddedOptimizeRule embeddedOptimizeRule =
    new EmbeddedOptimizeRule("classpath:embeddedOptimizeContext.xml");

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(embeddedOptimizeRule);

  @Test
  public void connectToSecuredElasticsearch() {
    // when I do a request against Optimize
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetAllAlertsRequest()
            .execute();

    // then Optimize should be able to successfully perform a request against elasticsearch
    assertThat(response.getStatus(), is(200));
  }


}
