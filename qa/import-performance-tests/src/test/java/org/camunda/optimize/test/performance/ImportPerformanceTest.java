package org.camunda.optimize.test.performance;

import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.util.NamedThreadFactory;
import org.camunda.optimize.service.util.VariableHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.util.PropertyUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_ID;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/import-performance-applicationContext.xml"})
public class ImportPerformanceTest {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  private EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();


  private static final int QUEUE_SIZE = 100;
  private long NUMBER_OF_PROCESS_INSTANCES;
  private long NUMBER_OF_ACTIVITY_INSTANCES;
  private long NUMBER_OF_VARIABLE_INSTANCES;
  private long NUMBER_OF_PROCESS_DEFINITIONS;
  private long maxImportDurationInMin;

  private ConfigurationService configurationService;

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Before
  public void setUp() throws SQLException {
    Properties properties = PropertyUtil.loadProperties("import-performance-test.properties");
    NUMBER_OF_PROCESS_DEFINITIONS =
      engineDatabaseRule.countProcessDefinitions();
    logger.info("The Camunda Platform contains {} process definitions.", NUMBER_OF_PROCESS_DEFINITIONS);
    NUMBER_OF_PROCESS_INSTANCES =
      engineDatabaseRule.countHistoricProcessInstances();
    logger.info("The Camunda Platform contains {} historic process instances.", NUMBER_OF_PROCESS_INSTANCES);
    NUMBER_OF_VARIABLE_INSTANCES =
      engineDatabaseRule.countHistoricVariableInstances();
    logger.info("The Camunda Platform contains {} historic variable instances.", NUMBER_OF_VARIABLE_INSTANCES);
    NUMBER_OF_ACTIVITY_INSTANCES =
      engineDatabaseRule.countHistoricActivityInstances();
    logger.info("The Camunda Platform contains {} historic activity instances.", NUMBER_OF_ACTIVITY_INSTANCES);

    maxImportDurationInMin = Long.parseLong(properties.getProperty("import.test.max.duration.in.min", "240"));
    elasticSearchRule.disableCleanup();
    configurationService = embeddedOptimizeRule.getConfigurationService();
  }

  @Test
  public void importPerformanceTest() throws Exception {
    //given I have data in the data
    OffsetDateTime beforeDataGeneration = OffsetDateTime.now();
    OffsetDateTime afterDataGeneration = OffsetDateTime.now();
    logger.info(
      "Data generation took [{}] min",
      ChronoUnit.MINUTES.between(beforeDataGeneration, afterDataGeneration));

    // when I import all data
    logger.info("Starting import of engine data to Optimize...");
    ScheduledExecutorService progressReporter = reportImportProgress();
    importEngineData();
    OffsetDateTime afterImport = OffsetDateTime.now();
    long importDurationInMinutes = ChronoUnit.MINUTES.between(afterDataGeneration, afterImport);
    logger.info("Import took [ " + importDurationInMinutes + " ] min");

    // then all data from the engine should be in Elasticsearch
    logger.info("Imported process definition count: {}", getImportedCountOf(configurationService.getProcessDefinitionType()));
    logger.info("Imported process instance count: {}", getImportedCountOf(configurationService.getProcessInstanceType()));
    logger.info("Imported variable count: {}", getVariableInstanceCount());
    logger.info("Imported activity count: {}", getActivityCount());
    assertThat(getImportedCountOf(configurationService.getProcessDefinitionType()), is(NUMBER_OF_PROCESS_DEFINITIONS));
    assertThat(getImportedCountOf(configurationService.getProcessInstanceType()), is(NUMBER_OF_PROCESS_INSTANCES));
    assertThat(getVariableInstanceCount(), is(NUMBER_OF_VARIABLE_INSTANCES));
    assertThat(getActivityCount(), is(NUMBER_OF_ACTIVITY_INSTANCES));
  }

  private void importEngineData() throws InterruptedException, TimeoutException {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(
      () -> embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities()
    );

    executor.shutdown();
    boolean wasAbleToFinishImportInTime =
      executor.awaitTermination(maxImportDurationInMin, TimeUnit.MINUTES);
    if (!wasAbleToFinishImportInTime) {
      throw new TimeoutException("Import was not able to finish import in " + maxImportDurationInMin + " minutes!");
    }
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
  }

  private ScheduledExecutorService reportImportProgress() {
    ScheduledExecutorService exec =
      Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(this.getClass().getSimpleName()));
    exec.scheduleAtFixedRate(
      () -> logger.info("Progress of engine import: {}%",
        computeImportProgress()), 0, 5, TimeUnit.SECONDS
    );
    return exec;
  }

  private long computeImportProgress() {
    // assumption: we know how many process instances have been generated
    Long processInstancesImported = getImportedCountOf(configurationService.getProcessInstanceType());
    Long totalInstances = Math.max(NUMBER_OF_PROCESS_INSTANCES, 1L);
    return Math.round(processInstancesImported.doubleValue() / totalInstances.doubleValue() * 100);
  }

  private Long getImportedCountOf(String elasticsearchType) {
    SearchResponse searchResponse = elasticSearchRule.getClient()
      .prepareSearch(configurationService.getOptimizeIndex(elasticsearchType))
      .setTypes(elasticsearchType)
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(0)
      .setFetchSource(false)
      .get();
    return searchResponse.getHits().getTotalHits();
  }

  private Long getActivityCount() {
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
      .get(EVENTS +"_count");
     return countAggregator.getValue();
  }

  private Long getVariableInstanceCount() {
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

    return totalVariableCount;
  }

}
