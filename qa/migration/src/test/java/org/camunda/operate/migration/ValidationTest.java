/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.camunda.operate.property.OperateProperties;

import static org.camunda.operate.es.schema.templates.ListViewTemplate.*;
import static org.camunda.operate.util.CollectionUtil.*;
import static org.camunda.operate.util.ElasticsearchUtil.*;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
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
		assertThat(importPositions.size()).isEqualTo(10);
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
		assertThat(sequenceFlows.size()).isEqualTo(202);
	}
	
	public void testActivityInstances() throws Throwable {
		assertAllIndexVersionsHasSameCounts("activity-instance");
		List<ActivityInstanceEntity> activityInstances = getEntitiesFor("activity-instance", ActivityInstanceEntity.class);
		assertThat(activityInstances.size()).isEqualTo(303);
		assertThat(activityInstances.stream().allMatch( a -> a.getType() != null)).as("All activity instances have a type").isTrue();
		assertThat(activityInstances.stream().allMatch( a -> a.getState()!= null)).as("All activity instances have a state").isTrue();
	}
	
	@Test
	public void testVariables() throws Throwable {
		assertAllIndexVersionsHasSameCounts("variable");
		List<VariableEntity> variableEntities = getEntitiesFor("variable", VariableEntity.class);
		assertThat(variableEntities.size()).isEqualTo(404);
	}
	
	@Test
	public void testOperations() throws Throwable {
		assertAllIndexVersionsHasSameCounts("operation");
		List<OperationEntity> operations = getEntitiesFor("operation", OperationEntity.class);
		// TODO: check operation (needs to create a operation from data generator
		assertThat(operations).isNotNull();
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
		assertThat(incidents.size()).isEqualTo(migrationProperties.getIncidentCount());
		assertThat(incidents.stream().allMatch(i -> i.getState() != null)).describedAs("Each incident has a state").isTrue();
		assertThat(incidents.stream().allMatch(i -> i.getErrorType() != null)).describedAs("Each incident has an errorType").isTrue();
	}
	
	protected void assertAllIndexVersionsHasSameCounts(String indexName) {
		List<String> versions = migrationProperties.getVersions();
		List<Long> hitsForVersions = map(versions, version -> getTotalHitsFor("operate-"+indexName+"-"+version+"_"));
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
		SearchRequest searchRequest = new SearchRequest(getIndexNameFor(index));
		searchRequest.source().size(1000);
		return searchEntitiesFor(searchRequest, entityClass);
	}
	
	protected <T> List<T> searchEntitiesFor(SearchRequest searchRequest,Class<T> entityClass) throws IOException{
		searchRequest.source().size(1000);
		SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
		return mapSearchHits(searchResponse.getHits().getHits(), objectMapper, entityClass);	
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
	
	protected String getIndexNameFor(String index) {
		return String.format("operate-%s-%s_", index,operateProperties.getSchemaVersion());
	}
	
}
