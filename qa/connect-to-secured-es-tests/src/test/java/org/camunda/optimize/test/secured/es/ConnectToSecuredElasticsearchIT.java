package org.camunda.optimize.test.secured.es;

import org.camunda.optimize.dto.optimize.query.OptimizeVersionDto;
import org.camunda.optimize.service.metadata.MetadataService;
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
    Response response =
      embeddedOptimizeRule.target("meta/version")
        .request()
        .get();

    // then Optimize should be able to perform a request against
    // Elasticsearch and return the correct result.
    assertThat(response.getStatus(), is(200));
    OptimizeVersionDto optimizeVersionDto = response.readEntity(OptimizeVersionDto.class);
    assertThat(
      optimizeVersionDto.getOptimizeVersion(),
      is(embeddedOptimizeRule.getApplicationContext().getBean(MetadataService.class).getVersion())
    );

  }


}
