/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Validator {

	private static final Logger logger = LoggerFactory.getLogger(DataGenerator.class);

	@Autowired
	private MigrationProperties migrationProperties;

	@Autowired
	private RestHighLevelClient esClient;
	
	final static List<String> VERSIONS = Arrays.asList("1.0.0","1.1.0","1.2.0");

	public void validate() throws IOException {
		esClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
		assertWorkflows();
		//assertWorkflowInstances();
		assertIncidents();
	}

	private void assertIncidents() {
		for(String version: VERSIONS) {
			assertCount(IncidentTemplate.INDEX_NAME, version, migrationProperties.getIncidentCount());
		}
	}

	private void assertWorkflows() {
		for(String version: VERSIONS) {
			assertCount(WorkflowIndex.INDEX_NAME, version, migrationProperties.getWorkflowCount());
		}
	}

	protected void assertCount(String indexName, String version, int expected) {
		try {
			CountResponse response = esClient.count(new CountRequest(getIndexName(indexName, version)),RequestOptions.DEFAULT);
			logger.info(String.format("%s version %s - expected: %d - actual: %d .", indexName, version, expected, response.getCount()));
		} catch (Throwable e) {
			logger.info(String.format("%s version %s - expected: %d - actual: %d .", indexName, version, expected, 0));
		}
	}

	public String getIndexName(String mainIndex, String version) {
		if (version == null ) {
			return String.format("%s-%s_", "operate", mainIndex);
		} else {
			return String.format("%s-%s-%s_", "operate", mainIndex, version);
		}
	}

}
