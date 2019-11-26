/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.SequenceFlowEntity;
import org.camunda.operate.entities.meta.ImportPositionEntity;
import org.camunda.operate.es.reader.ActivityInstanceReader;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.OperationReader;
import org.camunda.operate.es.reader.SequenceFlowReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.reader.WorkflowReader;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.es.schema.templates.ActivityInstanceTemplate;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.es.schema.templates.SequenceFlowTemplate;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/*
TODO: ??? operate-operation_         
TODO: with ESClient  operate-variable_  
TODO: with ESClient  operate-list-view_        
*/
@ContextConfiguration(classes = 
	  { MigrationProperties.class,Connector.class,ObjectMapper.class,OperateProperties.class,
		ListViewTemplate.class,ListViewReader.class,
		OperationReader.class,OperationTemplate.class,
		WorkflowReader.class,WorkflowIndex.class,
		WorkflowInstanceReader.class,
		IncidentReader.class,IncidentTemplate.class,
		SequenceFlowReader.class, SequenceFlowTemplate.class,
		ActivityInstanceReader.class,ActivityInstanceTemplate.class
})
public class ValidationTest {

	@Autowired
	MigrationProperties migrationProperties;

	@Autowired
	ObjectMapper objectMapper;
	
	@Autowired
	RestHighLevelClient esClient;

	@Autowired
	WorkflowReader workflowReader;
	
	@Autowired
	WorkflowInstanceReader workflowInstanceReader;

	@Autowired
	ListViewReader listViewReader;
	
	@Autowired
	IncidentReader incidentReader;
	
	@Autowired
	SequenceFlowReader sequenceFlowReader;
	
	@Autowired
	ActivityInstanceReader activityInstanceReader;
	
	Set<Long> workflowKeys;

	List<Long> workflowInstanceKeys;
	
	@Before
	public void setUp() {
		setupContext();
	    workflowKeys = getWorkflowKeys();
	    workflowInstanceKeys = getWorkflowInstanceKeys();
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

	private List<Long> getWorkflowInstanceKeys() {
		ListViewResponseDto listViewResponseDto = listViewReader.queryWorkflowInstances(new ListViewRequestDto(ListViewQueryDto.createAll()),0,migrationProperties.getWorkflowInstanceCount());
	    return CollectionUtil.map(listViewResponseDto.getWorkflowInstances(), wfi -> Long.valueOf(wfi.getId()));
	}

	private LinkedHashSet<Long> getWorkflowKeys() {
		return new LinkedHashSet<Long>(workflowReader.getWorkflows().keySet());
	}
	
	@Ignore @Test
	public void testImportPositions() throws Throwable {
		SearchRequest searchRequest = new SearchRequest("operate-import-position-1.2.0_");
		searchRequest.source().size(1000);
		SearchResponse searchResponse;
		searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
		List<ImportPositionEntity> importPositions = CollectionUtil.map(searchResponse.getHits().getHits(), hit ->
				ElasticsearchUtil.fromSearchHit(hit.getSourceAsString(), objectMapper,ImportPositionEntity.class)
		);
		assertThat(importPositions.size()).isEqualTo(10);
	}
	
	@Test
	public void testEvents() throws Throwable {
		SearchRequest searchRequest = new SearchRequest("operate-event-1.2.0_");
		searchRequest.source().size(1000);
		SearchResponse searchResponse;
		searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
		List<EventEntity> events = CollectionUtil.map(searchResponse.getHits().getHits(), hit ->
				ElasticsearchUtil.fromSearchHit(hit.getSourceAsString(), objectMapper,EventEntity.class)
		);
		assertThat(events.size()).isEqualTo(644);
	}
	
	@Test

	public void testSequenceFlows() throws Throwable {
		List<SequenceFlowEntity> sequenceFlowEntities = new ArrayList<>();
		workflowInstanceKeys.forEach(key -> {
				sequenceFlowEntities.addAll(sequenceFlowReader.getSequenceFlowsByWorkflowInstanceKey(key));
		});
		assertThat(sequenceFlowEntities.size()).isEqualTo(202);
	}
	
	public void testActivityInstances() throws Throwable {
		List<ActivityInstanceEntity> activityInstanceEntities = new ArrayList<>();
		workflowInstanceKeys.forEach(key -> {
			activityInstanceEntities.addAll(activityInstanceReader.getAllActivityInstances(key));
		});
		assertThat(activityInstanceEntities.size()).isEqualTo(303);
	}
	

	public void testVariables() throws Throwable {
		
	}
	
	@Test
	public void testWorkflows() throws IOException {	
		assertThat(workflowKeys.size()).isEqualTo(migrationProperties.getWorkflowCount());
	}
	
	@Test
	public void testWorkflowInstances() {
		assertThat(workflowInstanceKeys.size()).isEqualTo(migrationProperties.getWorkflowInstanceCount());
	}
	
	@Test
	public void testIncidents() {
		final List<IncidentEntity> incidents = new ArrayList<IncidentEntity>();
		workflowInstanceKeys.forEach(workflowInstanceKey -> {
			incidents.addAll(incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey));
		});
		assertThat(incidents.size()).isEqualTo(migrationProperties.getIncidentCount());
	}

}
