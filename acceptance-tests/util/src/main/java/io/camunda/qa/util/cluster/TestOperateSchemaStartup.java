/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import io.camunda.operate.schema.SchemaStartup;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaStartup")
@Profile("test")
public class TestOperateSchemaStartup extends SchemaStartup {}
