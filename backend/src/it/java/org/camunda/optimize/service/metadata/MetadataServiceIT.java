package org.camunda.optimize.service.metadata;

import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.es.reader.MetadataReader;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;


public class MetadataServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule);

  @Test
  public void verifyVersionIsInitialized() throws Exception {
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    String version = embeddedOptimizeRule.getApplicationContext()
      .getBean(MetadataReader.class).readMetadata().get().getSchemaVersion();
    String expected = embeddedOptimizeRule.getApplicationContext().getBean(MetadataService.class).getVersion();
    assertThat(version, is(expected));
  }

  @Test
  public void verifyNotStartingIfMetadataIsCorrupted() throws Exception {
    String metaDataType = ElasticsearchConstants.METADATA_TYPE;
    embeddedOptimizeRule.stopOptimize();
    MetadataDto meta = new MetadataDto();
    meta.setSchemaVersion("TEST");
    elasticSearchRule.addEntryToElasticsearch(metaDataType, "2", meta);
    elasticSearchRule.addEntryToElasticsearch(metaDataType, "3", meta);
    try {
      embeddedOptimizeRule.startOptimize();
    } catch (Exception e) {
      //expected
      elasticSearchRule.deleteAllOptimizeData();
      embeddedOptimizeRule.stopOptimize();
      embeddedOptimizeRule.startOptimize();
      return;
    }

    fail("Exception expected");
  }

  @Test
  public void verifyNotStartingIfVersionDoesNotMatch () throws Exception {
    String metaDataType = ElasticsearchConstants.METADATA_TYPE;
    embeddedOptimizeRule.stopOptimize();
    elasticSearchRule.deleteAllOptimizeData();
    MetadataDto meta = new MetadataDto();
    meta.setSchemaVersion("TEST");
    elasticSearchRule.addEntryToElasticsearch(metaDataType, "2", meta);
    try {
      embeddedOptimizeRule.startOptimize();
    } catch (Exception e) {
      //expected
      assertThat(e.getCause().getMessage(), is("The Elasticsearch data structure doesn't match the current Optimize version"));
      elasticSearchRule.deleteAllOptimizeData();
      embeddedOptimizeRule.stopOptimize();
      embeddedOptimizeRule.startOptimize();
      return;
    }

    fail("Exception expected");
  }
}
