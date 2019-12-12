/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.VARIABLES_JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.camunda.operate.util.CollectionUtil.map;
import static org.camunda.operate.util.ElasticsearchUtil.mapSearchHits;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.SequenceFlowEntity;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.VariableForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.meta.ImportPositionEntity;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.webapp.es.reader.IncidentStatisticsReader;
import org.camunda.operate.webapp.es.reader.WorkflowInstanceReader;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ContextConfiguration(
	classes = { 
		Connector.class,ObjectMapper.class,
		OperateProperties.class,MigrationProperties.class
	}
)
public class ValidationTest {

	@Autowired
	MigrationProperties migrationProperties;

	@Autowired
	OperateProperties operateProperties;
	
	@Autowired
	ObjectMapper objectMapper;
	
	@Autowired
	RestHighLevelClient esClient;
	
	@Before
	public void setUp() {
		setupContext();
	}
	
	@Test
	public void testImportPositions() throws Throwable {
		assertAllIndexVersionsHasSameCounts("import-position");
		List<ImportPositionEntity> importPositions = getEntitiesFor("import-position", ImportPositionEntity.class);
		assertThat(importPositions.isEmpty()).describedAs("There should exists at least 1 ImportPosition").isFalse();
	}
	
	@Test
	public void testEvents() throws Throwable {
		assertAllIndexVersionsHasSameCounts("event");
		List<EventEntity> events = getEntitiesFor("event", EventEntity.class);
		assertThat(events.isEmpty()).isFalse();
		assertThat(events.stream().filter(e -> e.getMetadata() != null).count()).describedAs("At least one event has metadata").isGreaterThan(0);
		assertThat(events.stream().allMatch(e -> e.getEventSourceType()!= null)).describedAs("All events have a EventSourceType").isTrue();
		assertThat(events.stream().allMatch(e -> e.getEventType() != null)).describedAs("All events have a EventType").isTrue();
	}
	
	@Test
	public void testSequenceFlows() throws Throwable {
		assertAllIndexVersionsHasSameCounts("sequence-flow");
		List<SequenceFlowEntity> sequenceFlows = getEntitiesFor("sequence-flow", SequenceFlowEntity.class);
		assertThat(sequenceFlows.size()).isEqualTo(migrationProperties.getWorkflowInstanceCount() * 2);
	}
	
	@Test
	public void testActivityInstances() throws Throwable {
		assertAllIndexVersionsHasSameCounts("activity-instance");
		List<ActivityInstanceEntity> activityInstances = getEntitiesFor("activity-instance", ActivityInstanceEntity.class);
		assertThat(activityInstances.size()).isEqualTo(migrationProperties.getWorkflowInstanceCount() * 3);
		assertThat(activityInstances.stream().allMatch( a -> a.getType() != null)).as("All activity instances have a type").isTrue();
		assertThat(activityInstances.stream().allMatch( a -> a.getState()!= null)).as("All activity instances have a state").isTrue();
	}
	
	@Test
	public void testVariables() throws Throwable {
		assertAllIndexVersionsHasSameCounts("variable");
		List<VariableEntity> variableEntities = getEntitiesFor("variable", VariableEntity.class);
		assertThat(variableEntities.size()).isEqualTo(migrationProperties.getWorkflowInstanceCount() * 4);
	}
	
	@Test
	public void testOperations() throws Throwable {
		assertAllIndexVersionsHasSameCounts("operation");
		List<OperationEntity> operations = getEntitiesFor("operation", OperationEntity.class);
		assertThat(operations.size()).describedAs("At least one operation is active").isGreaterThan(0);
	}
        	
	@Test
	public void testListViews() throws Throwable {
		assertAllIndexVersionsHasSameCounts("list-view");
		SearchRequest searchRequest = new SearchRequest(getIndexNameFor("list-view"));
		int workflowInstancesCount = migrationProperties.getWorkflowInstanceCount();
		
		// Workflow instances list
		searchRequest.source().query(termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION));
		List<WorkflowInstanceForListViewEntity> workflowInstancesList = searchEntitiesFor(searchRequest, WorkflowInstanceForListViewEntity.class);
		assertThat(workflowInstancesList.size()).isEqualTo(workflowInstancesCount * 1);
		
		//  Variables list
		searchRequest.source().query(termQuery(JOIN_RELATION, VARIABLES_JOIN_RELATION));
		List<VariableForListViewEntity> variablesList = searchEntitiesFor(searchRequest, VariableForListViewEntity.class);
		assertThat(variablesList.size()).isEqualTo(workflowInstancesCount * 4);
		
		// Activity instances list
		searchRequest.source().query(termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION));
		List<ActivityInstanceForListViewEntity> activitiesList = searchEntitiesFor(searchRequest, ActivityInstanceForListViewEntity.class);
		assertThat(activitiesList.size()).isEqualTo(workflowInstancesCount * 3);
	}
	
	@Test
	public void testWorkflows() throws IOException {	
		assertAllIndexVersionsHasSameCounts("workflow");
		List<WorkflowEntity> workflows = getEntitiesFor("workflow", WorkflowEntity.class);
		assertThat(workflows.size()).isEqualTo(migrationProperties.getWorkflowCount());
	}
	
	@Test
	public void testIncidents() throws IOException {
		assertAllIndexVersionsHasSameCounts("incident");
		List<IncidentEntity> incidents = getEntitiesFor("incident", IncidentEntity.class);
		assertThat(incidents.size()).isEqualTo(migrationProperties.getIncidentCount() - migrationProperties.getCountOfResolveOperation());
		assertThat(incidents.stream().allMatch(i -> i.getState() != null)).describedAs("Each incident has a state").isTrue();
		assertThat(incidents.stream().allMatch(i -> i.getErrorType() != null)).describedAs("Each incident has an errorType").isTrue();
	}
	
	@Test
	public void testCoreStatistics() throws IOException {
    final SearchRequest searchRequest = new SearchRequest(
        getIndexNameFor(ListViewTemplate.INDEX_NAME))
        .source(new SearchSourceBuilder().size(0)
            .aggregation(WorkflowInstanceReader.INCIDENTS_AGGREGATION)
            .aggregation(WorkflowInstanceReader.RUNNING_AGGREGATION)
        );

     final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
     Aggregations aggregations = response.getAggregations();
     long runningCount = ((SingleBucketAggregation) aggregations.get("running")).getDocCount();
     long incidentCount = ((SingleBucketAggregation) aggregations.get("incidents")).getDocCount();
     assertThat(runningCount).isEqualTo(migrationProperties.getWorkflowInstanceCount() - migrationProperties.getCountOfCancelOperation());
     assertThat(incidentCount).isEqualTo(migrationProperties.getIncidentCount() - migrationProperties.getCountOfResolveOperation());
  }
	
  @Test
	public void testIncidentsStatistics() throws IOException {
	  List<Long> workflowsKeys = map(getEntitiesFor("workflow", WorkflowEntity.class),WorkflowEntity::getKey);
	  long savedIncidents = getEntitiesFor("incident", IncidentEntity.class).size();
	  
    SearchRequest searchRequest = new SearchRequest(getIndexNameFor(ListViewTemplate.INDEX_NAME))
        .source(new SearchSourceBuilder()
            .query(IncidentStatisticsReader.INCIDENTS_QUERY)
            .aggregation(IncidentStatisticsReader.COUNT_WORKFLOW_KEYS).size(0));

      SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      List<? extends Bucket> buckets = ((Terms) searchResponse.getAggregations().get(IncidentStatisticsReader.WORKFLOW_KEYS)).getBuckets();
      
      long incidentsCount = 0;
      for (Bucket bucket : buckets) {
        Long workflowKey = (Long) bucket.getKey();
        incidentsCount += bucket.getDocCount();
        assertThat(workflowKey).isIn(workflowsKeys);
      }
      assertThat(incidentsCount).isEqualTo(savedIncidents);
  }

	protected void setupContext() {
		TestContextManager testContextManager = new TestContextManager(getClass());
	    try {
	      testContextManager.prepareTestInstance(this);
	      objectMapper.registerModule(new JavaTimeModule());
	    } catch (Exception e) {
	      throw new RuntimeException("Failed to initialize context manager", e);
	    }
	}
	
	protected void assertAllIndexVersionsHasSameCounts(String indexName) {
		List<String> versions = migrationProperties.getVersions();
		List<Long> hitsForVersions = map(versions, version -> getTotalHitsFor(getIndexNameFor(indexName,version)));
		assertThat(new HashSet<>(hitsForVersions).size()).as("Checks %s index for versions %s has the same counts", indexName, versions).isEqualTo(1);
	}

	protected long getTotalHitsFor(String indexName) {
		try {
			return esClient.search(new SearchRequest(indexName), RequestOptions.DEFAULT).getHits().getTotalHits();
		} catch (Throwable e) {
			return 0;
		}
	}
	
	protected <T> List<T> getEntitiesFor(String index,Class<T> entityClass) throws IOException{
		return searchEntitiesFor(new SearchRequest(getIndexNameFor(index)), entityClass);
	}
	
	protected <T> List<T> searchEntitiesFor(SearchRequest searchRequest,Class<T> entityClass) throws IOException{
		searchRequest.source().size(1000);
		SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
		return mapSearchHits(searchResponse.getHits().getHits(), objectMapper, entityClass);	
	}
	
	protected String getIndexNameFor(String index) {
		return getIndexNameFor(index, OperateProperties.getSchemaVersion());
	}
	
	protected String getIndexNameFor(String index,String version) {
		return String.format("operate-%s-%s_", index,version);
	}
	
}
