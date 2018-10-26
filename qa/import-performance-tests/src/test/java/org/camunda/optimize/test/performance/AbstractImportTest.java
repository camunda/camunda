package org.camunda.optimize.test.performance;

import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.util.NamedThreadFactory;
import org.camunda.optimize.service.util.VariableHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_ID;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/import-applicationContext.xml"})
public abstract class AbstractImportTest {
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final Properties properties = getProperties();

  protected ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  protected EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  protected EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule(properties);
  protected long maxImportDurationInMin;
  protected ConfigurationService configurationService;

  @Rule
  public RuleChain chain = RuleChain.outerRule(elasticSearchRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  abstract Properties getProperties();

  @Before
  public void setUp() {
    maxImportDurationInMin = Long.parseLong(properties.getProperty("import.test.max.duration.in.min", "240"));
    elasticSearchRule.disableCleanup();
    configurationService = embeddedOptimizeRule.getConfigurationService();
  }

  protected void logStats() {
    try {
      logger.info(
        "The Camunda Platform contains {} process definitions. Optimize: {}",
        (engineDatabaseRule.countProcessDefinitions()),
        getImportedCountOf(configurationService.getProcessDefinitionType())
      );
      logger.info(
        "The Camunda Platform contains {} historic process instances. Optimize: {}",
        engineDatabaseRule.countHistoricProcessInstances(),
        getImportedCountOf(configurationService.getProcessInstanceType())
      );
      logger.info(
        "The Camunda Platform contains {} historic variable instances. Optimize: {}",
        engineDatabaseRule.countHistoricVariableInstances(),
        getVariableInstanceCount()
      );
      logger.info(
        "The Camunda Platform contains {} historic activity instances. Optimize: {}",
        engineDatabaseRule.countHistoricActivityInstances(),
        getActivityCount()
      );
    } catch (SQLException e) {
      logger.error("Failed producing stats", e);
    }
  }

  protected ScheduledExecutorService reportImportProgress() {
    ScheduledExecutorService exec =
      Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(this.getClass().getSimpleName()));
    exec.scheduleAtFixedRate(
      () -> {
        logger.info("Progress of engine import: {}%", computeImportProgress());
      },
      0,
      5,
      TimeUnit.SECONDS
    );
    return exec;
  }

  private long computeImportProgress() {
    // assumption: we know how many process instances have been generated
    Integer processInstancesImported = getImportedCountOf(configurationService.getProcessInstanceType());
    Long totalInstances = null;
    try {
      totalInstances = Math.max(engineDatabaseRule.countHistoricProcessInstances(), 1L);
      return Math.round(processInstancesImported.doubleValue() / totalInstances.doubleValue() * 100);
    } catch (SQLException e) {
      e.printStackTrace();
      return 0L;
    }
  }

  protected Integer getImportedCountOf(String elasticsearchType) {
    SearchResponse searchResponse = elasticSearchRule.getClient()
      .prepareSearch(configurationService.getOptimizeIndex(elasticsearchType))
      .setTypes(elasticsearchType)
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(0)
      .setFetchSource(false)
      .get();
    return Long.valueOf(searchResponse.getHits().getTotalHits()).intValue();
  }

  protected Integer getActivityCount() {
    SearchResponse response = elasticSearchRule.getClient()
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(0)
      .addAggregation(
        nested(EVENTS, EVENTS)
          .subAggregation(
            count(EVENTS + "_count")
              .field(EVENTS + "." + ProcessInstanceType.EVENT_ID)
          )
      )
      .setFetchSource(false)
      .get();

    Nested nested = response.getAggregations()
      .get(EVENTS);
    ValueCount countAggregator =
      nested.getAggregations()
        .get(EVENTS + "_count");
    return Long.valueOf(countAggregator.getValue()).intValue();
  }

  protected Integer getVariableInstanceCount() {
    SearchRequestBuilder searchRequestBuilder = elasticSearchRule.getClient()
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(0)
      .setFetchSource(false);

    for (String variableTypeFieldLabel : VariableHelper.allVariableTypeFieldLabels) {
      searchRequestBuilder.addAggregation(
        nested(variableTypeFieldLabel, variableTypeFieldLabel)
          .subAggregation(
            count(variableTypeFieldLabel + "_count")
              .field(variableTypeFieldLabel + "." + VARIABLE_ID)
          )
      );
    }

    SearchResponse response = searchRequestBuilder.get();

    long totalVariableCount = 0L;
    for (String variableTypeFieldLabel : VariableHelper.allVariableTypeFieldLabels) {
      Nested nestedAgg = response.getAggregations().get(variableTypeFieldLabel);
      ValueCount countAggregator = nestedAgg.getAggregations()
        .get(variableTypeFieldLabel + "_count");
      totalVariableCount += countAggregator.getValue();
    }

    return Long.valueOf(totalVariableCount).intValue();
  }

  protected void assertThatEngineAndElasticDataMatch() throws SQLException {
    assertThat(
      "processDefinitionsCount",
      getImportedCountOf(configurationService.getProcessDefinitionType()),
      is(engineDatabaseRule.countProcessDefinitions())
    );
    assertThat(
      "processInstanceTypeCount",
      getImportedCountOf(configurationService.getProcessInstanceType()),
      is(engineDatabaseRule.countHistoricProcessInstances())
    );
    assertThat(
      "variableInstanceCount",
      getVariableInstanceCount(),
      is(engineDatabaseRule.countHistoricVariableInstances())
    );
    assertThat(
      "historicActivityInstanceCount",
      getActivityCount(),
      is(engineDatabaseRule.countHistoricActivityInstances())
    );
  }
}
