/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import static org.camunda.operate.webapp.security.WebSecurityConfig.LOGIN_RESOURCE;
import static org.camunda.operate.webapp.security.WebSecurityConfig.X_CSRF_HEADER;
import static org.camunda.operate.webapp.security.WebSecurityConfig.X_CSRF_TOKEN;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.camunda.operate.entities.OperationType;
import org.camunda.operate.util.CollectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.client.TestRestTemplate.HttpClientOption;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
public class RestClient {

	private static final Logger logger = LoggerFactory.getLogger(RestClient.class);
	private TestRestTemplate testRestTemplate = new TestRestTemplate(HttpClientOption.ENABLE_COOKIES);
	
	private HttpEntity<Map<String, String>> loggedInHeaders;
	
	@Autowired
	private MigrationProperties migrationProperties;
	
	@PostConstruct
	protected void setUp() {
		login("demo", "demo");
	}
	
	public RestClient login(String username,String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("username", username);
		body.add("password", password);
		loggedInHeaders = prepareRequestWithCookies(testRestTemplate
				.postForEntity( migrationProperties.getFromOperateBaseUrl() + LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class));
		logger.info("Logged in as {}",loggedInHeaders);
		return this;
	}
	
	public boolean createOperation(Long workflowInstanceKey, OperationType operationType) {
		Map<String,Object> operationRequest = CollectionUtil.asMap("operationType",operationType.name());
		String apiEndpoint = migrationProperties.getFromOperateBaseUrl()+"/api/workflow-instances/" + workflowInstanceKey + "/operation";
		ResponseEntity<Map> operationResponse = testRestTemplate.postForEntity(apiEndpoint,operationRequest,Map.class, loggedInHeaders);
		logger.info("OperationResponse: {}",operationResponse.getBody());
		return operationResponse.getStatusCode().equals(HttpStatus.OK) && operationResponse.getBody().get("count").equals(1);
	}
	  
	private HttpEntity<Map<String, String>> prepareRequestWithCookies(ResponseEntity<?> response) {
		HttpHeaders headers = getHeaderWithCSRF(response.getHeaders());
		headers.setContentType(APPLICATION_JSON);
		headers.add("Cookie", response.getHeaders().get("Set-Cookie").get(0));

		Map<String, String> body = new HashMap<>();

		return new HttpEntity<>(body, headers);
	}

	private HttpHeaders getHeaderWithCSRF(HttpHeaders responseHeaders) {
		HttpHeaders headers = new HttpHeaders();
		if (responseHeaders.containsKey(X_CSRF_HEADER)) {
			String csrfHeader = responseHeaders.get(X_CSRF_HEADER).get(0);
			String csrfToken = responseHeaders.get(X_CSRF_TOKEN).get(0);
			headers.set(csrfHeader, csrfToken);
		}
		return headers;
	}

}
