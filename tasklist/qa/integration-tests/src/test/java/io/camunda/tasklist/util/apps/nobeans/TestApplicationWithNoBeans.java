/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util.apps.nobeans;

import com.graphql.spring.boot.test.GraphQLTestAutoConfiguration;
import graphql.kickstart.autoconfigure.annotations.GraphQLAnnotationsAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    exclude = {GraphQLTestAutoConfiguration.class, GraphQLAnnotationsAutoConfiguration.class})
public class TestApplicationWithNoBeans {}
