/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import java.io.IOException;
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
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.VARIABLES_JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.camunda.operate.util.CollectionUtil.chooseOne;
import static org.camunda.operate.util.CollectionUtil.map;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@ContextConfiguration(
	classes = { 
		Connector.class,EntityReader.class,ObjectMapper.class,
		OperateProperties.class,MigrationProperties.class
	}
)
public class ValidationTest {

	@Autowired
	private MigrationProperties config;
	
	@Autowired
	private RestHighLevelClient esClient;

	@Autowired
	private OperateProperties operateProperties;
	
	@Autowired
	private BeanFactory beanFactory;

  private EntityReader entityReader;
	
	@Before
	public void setUp() {
		setupContext();
	  entityReader = beanFactory.getBean(EntityReader.class,operateProperties.getSchemaVersion());
	}
	
	@Test
	public void testImportPositions() throws Throwable {
		List<ImportPositionEntity> importPositions = entityReader.getEntitiesFor("import-position", ImportPositionEntity.class);
		assertThat(importPositions.isEmpty()).describedAs("There should exists at least 1 ImportPosition").isFalse();
	}
	
	@Test
	public void testEvents() throws Throwable {
		List<EventEntity> events = entityReader.getEntitiesFor("event", EventEntity.class);
		assertThat(events.isEmpty()).isFalse();
		assertThat(events.stream().filter(e -> e.getMetadata() != null).count()).describedAs("At least one event has metadata").isGreaterThan(0);
		assertThat(events.stream().allMatch(e -> e.getEventSourceType()!= null)).describedAs("All events have a EventSourceType").isTrue();
		assertThat(events.stream().allMatch(e -> e.getEventType() != null)).describedAs("All events have a EventType").isTrue();
	}
	
	@Test
	public void testSequenceFlows() throws Throwable {
		List<SequenceFlowEntity> sequenceFlows = entityReader.getEntitiesFor("sequence-flow", SequenceFlowEntity.class);
		assertThat(sequenceFlows.size()).isEqualTo(config.getWorkflowInstanceCount() * 2);
	}
	
	@Test
	public void testActivityInstances() throws Throwable {
		List<ActivityInstanceEntity> activityInstances = entityReader.getEntitiesFor("activity-instance", ActivityInstanceEntity.class);
		assertThat(activityInstances.size()).isEqualTo(config.getWorkflowInstanceCount() * 3);
		assertThat(activityInstances.stream().allMatch( a -> a.getType() != null)).as("All activity instances have a type").isTrue();
		assertThat(activityInstances.stream().allMatch( a -> a.getState()!= null)).as("All activity instances have a state").isTrue();
	}
	
	@Test
	public void testVariables() throws Throwable {
		List<VariableEntity> variableEntities = entityReader.getEntitiesFor("variable", VariableEntity.class);
		assertThat(variableEntities.size()).isEqualTo(config.getWorkflowInstanceCount() * 4);
	}
	
	@Test
	public void testOperations() throws Throwable {
		List<OperationEntity> operations = entityReader.getEntitiesFor("operation", OperationEntity.class);
		assertThat(operations.size()).describedAs("At least one operation is active").isGreaterThan(0);
	}
        	
	@Test
	public void testListViews() throws Throwable {
		SearchRequest searchRequest = new SearchRequest(entityReader.getAliasFor(ListViewTemplate.INDEX_NAME));
		int workflowInstancesCount = config.getWorkflowInstanceCount();
		
		// Workflow instances list
		searchRequest.source().query(termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION));
		List<WorkflowInstanceForListViewEntity> workflowInstancesList = entityReader.searchEntitiesFor(searchRequest, WorkflowInstanceForListViewEntity.class);
		assertThat(workflowInstancesList.size()).isEqualTo(workflowInstancesCount * 1);
		
		//  Variables list
		searchRequest.source().query(termQuery(JOIN_RELATION, VARIABLES_JOIN_RELATION));
		List<VariableForListViewEntity> variablesList = entityReader.searchEntitiesFor(searchRequest, VariableForListViewEntity.class);
		assertThat(variablesList.size()).isEqualTo(workflowInstancesCount * 4);
		
		// Activity instances list
		searchRequest.source().query(termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION));
		List<ActivityInstanceForListViewEntity> activitiesList = entityReader.searchEntitiesFor(searchRequest, ActivityInstanceForListViewEntity.class);
		assertThat(activitiesList.size()).isEqualTo(workflowInstancesCount * 3);
	}
	
	@Test
	public void testWorkflows() throws IOException {	
		List<WorkflowEntity> workflows = entityReader.getEntitiesFor("workflow", WorkflowEntity.class);
		assertThat(workflows.size()).isEqualTo(config.getWorkflowCount());
	}
	
	@Test
	public void testIncidents() throws IOException {
		List<IncidentEntity> incidents = entityReader.getEntitiesFor("incident", IncidentEntity.class);
		assertThat(incidents.size()).isBetween(
		    config.getIncidentCount() - (config.getCountOfCancelOperation() + config.getCountOfResolveOperation()),
		    config.getIncidentCount() 
		);
		assertThat(incidents.stream().allMatch(i -> i.getState() != null)).describedAs("Each incident has a state").isTrue();
		assertThat(incidents.stream().allMatch(i -> i.getErrorType() != null)).describedAs("Each incident has an errorType").isTrue();
		IncidentEntity randomIncident = chooseOne(incidents);
		assertThat(randomIncident.getErrorMessageHash()).isNotNull();
	}
	
	@Test
	public void testCoreStatistics() throws IOException {
    final SearchRequest searchRequest = new SearchRequest(
        entityReader.getAliasFor(ListViewTemplate.INDEX_NAME))
        .source(new SearchSourceBuilder().size(0)
            .aggregation(WorkflowInstanceReader.INCIDENTS_AGGREGATION)
            .aggregation(WorkflowInstanceReader.RUNNING_AGGREGATION)
        );

     final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
     long runningCount = getAggregationCountFor(response,"running");
     long incidentCount = getAggregationCountFor(response, "incidents");
     assertThat(runningCount).isBetween(
       (long)config.getWorkflowInstanceCount() - config.getCountOfCancelOperation(),
       (long) config.getWorkflowInstanceCount()
     );
     assertThat(incidentCount).isBetween(
        (long)config.getIncidentCount() - (config.getCountOfCancelOperation() + config.getCountOfResolveOperation()),
        (long) config.getIncidentCount()
     );
  }

	protected long getAggregationCountFor(SearchResponse response, String aggregationName) {
	  Aggregations aggregations = response.getAggregations();
    return ((SingleBucketAggregation) aggregations.get(aggregationName)).getDocCount();
  }
	
  @Test
	public void testIncidentsStatistics() throws IOException {
	  List<Long> workflowsKeys = map(entityReader.getEntitiesFor("workflow", WorkflowEntity.class),WorkflowEntity::getKey);
	  long savedIncidents = entityReader.getEntitiesFor("incident", IncidentEntity.class).size();
	  
    SearchRequest searchRequest = new SearchRequest(entityReader.getAliasFor(ListViewTemplate.INDEX_NAME))
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
	    } catch (Exception e) {
	      throw new RuntimeException("Failed to initialize context manager", e);
	    }
	}
	
}
