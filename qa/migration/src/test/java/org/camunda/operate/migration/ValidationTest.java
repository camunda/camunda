/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.SequenceFlowEntity;
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
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
	
	@Test
	public void testImportPositions() throws Throwable {
		assertAllIndexVersionsHasSameCounts("operate-import-position");
		// Details:
//		SearchRequest searchRequest = new SearchRequest("operate-import-position-1.2.0_");
//		searchRequest.source().size(1000);
//		SearchResponse searchResponse;
//		searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
//		List<ImportPositionEntity> importPositions = CollectionUtil.map(searchResponse.getHits().getHits(), hit ->
//				ElasticsearchUtil.fromSearchHit(hit.getSourceAsString(), objectMapper,ImportPositionEntity.class)
//		);
//		assertThat(importPositions.size()).isEqualTo(10);
	}
	
	@Test
	public void testEvents() throws Throwable {
		assertAllIndexVersionsHasSameCounts("operate-event");
	}
	
	@Test

	public void testSequenceFlows() throws Throwable {
		assertAllIndexVersionsHasSameCounts("operate-sequence-flow");
		// Details:
		List<SequenceFlowEntity> sequenceFlowEntities = new ArrayList<>();
		workflowInstanceKeys.forEach(key -> {
				sequenceFlowEntities.addAll(sequenceFlowReader.getSequenceFlowsByWorkflowInstanceKey(key));
		});
		assertThat(sequenceFlowEntities.size()).isEqualTo(202);
	}
	
	public void testActivityInstances() throws Throwable {
		assertAllIndexVersionsHasSameCounts("operate-activity-instance");
		// Details:
		List<ActivityInstanceEntity> activityInstanceEntities = new ArrayList<>();
		workflowInstanceKeys.forEach(key -> {
			activityInstanceEntities.addAll(activityInstanceReader.getAllActivityInstances(key));
		});
		assertThat(activityInstanceEntities.size()).isEqualTo(303);
	}
	
	@Test
	public void testVariables() throws Throwable {
		assertAllIndexVersionsHasSameCounts("operate-variable");
	}
	
	@Test
	public void testOperations() throws Throwable {
		assertAllIndexVersionsHasSameCounts("operate-operation");
	}
        	
	@Test
	public void testListViews() throws Throwable {
		assertAllIndexVersionsHasSameCounts("operate-list-view");
	}
	
	@Test
	public void testWorkflows() throws IOException {	
		assertAllIndexVersionsHasSameCounts("operate-workflow");
		assertThat(workflowKeys.size()).isEqualTo(migrationProperties.getWorkflowCount());
	}
	
	@Test
	public void testWorkflowInstances() {
		assertThat(workflowInstanceKeys.size()).isEqualTo(migrationProperties.getWorkflowInstanceCount());
	}
	
	@Test
	public void testIncidents() {
		assertAllIndexVersionsHasSameCounts("operate-incident");
		// Details:
		final List<IncidentEntity> incidents = new ArrayList<IncidentEntity>();
		workflowInstanceKeys.forEach(workflowInstanceKey -> {
			incidents.addAll(incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey));
		});
		assertThat(incidents.size()).isEqualTo(migrationProperties.getIncidentCount());
	}
	
	protected void assertAllIndexVersionsHasSameCounts(String indexName) {
		List<String> versions = migrationProperties.getVersions();
		List<Long> hitsForVersions = CollectionUtil.map(versions, version -> getTotalHitsFor(indexName+"-"+version+"_"));
		assertThat(new HashSet<>(hitsForVersions).size()).as("Checks %s index for versions %s has the same counts", indexName, versions).isEqualTo(1);
	}

	protected long getTotalHitsFor(String indexName) {
		try {
			return esClient.search(new SearchRequest(indexName), RequestOptions.DEFAULT).getHits().getTotalHits();
		} catch (Throwable e) {
			return 0;
		}
	}
	
}
