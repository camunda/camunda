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

import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.CollectionUtil;
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
	  { MigrationProperties.class,Connector.class,ObjectMapper.class,OperateProperties.class
})
public class ValidationTest {

	@Autowired
	MigrationProperties migrationProperties;

	@Autowired
	ObjectMapper objectMapper;
	
	@Autowired
	RestHighLevelClient esClient;
	
	@Before
	public void setUp() {
		setupContext();
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
	}
	
	public void testActivityInstances() throws Throwable {
		assertAllIndexVersionsHasSameCounts("operate-activity-instance");
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
	}
	
	@Test
	public void testIncidents() {
		assertAllIndexVersionsHasSameCounts("operate-incident");
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
