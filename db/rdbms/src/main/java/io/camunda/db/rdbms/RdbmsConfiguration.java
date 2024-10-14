/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.read.service.ProcessDefinitionReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader;
import io.camunda.db.rdbms.read.service.VariableReader;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.service.ExporterPositionService;
import io.camunda.zeebe.scheduler.ActorScheduler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(MyBatisConfiguration.class)
public class RdbmsConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdbmsConfiguration.class);

  @Bean
  public ExecutionQueue executionQueue(
      final ActorScheduler actorScheduler, final SqlSessionFactory sqlSessionFactory) {
    return new ExecutionQueue(actorScheduler, sqlSessionFactory);
  }

  @Bean
  public VariableReader variableRdbmsReader(final VariableMapper variableMapper) {
    return new VariableReader(variableMapper);
  }

  @Bean
  public ProcessDefinitionReader processDeploymentRdbmsReader(
      final ProcessDefinitionMapper processDefinitionMapper) {
    return new ProcessDefinitionReader(processDefinitionMapper);
  }

  @Bean
  public ProcessInstanceReader processRdbmsReader(
      final ProcessInstanceMapper processInstanceMapper) {
    return new ProcessInstanceReader(processInstanceMapper);
  }

  @Bean
  public ExporterPositionService exporterPositionRdbmsService(
      final ExecutionQueue executionQueue, final ExporterPositionMapper exporterPositionMapper) {
    return new ExporterPositionService(executionQueue, exporterPositionMapper);
  }

  @Bean
  public RdbmsService rdbmsService(
      final ExecutionQueue executionQueue,
      final ExporterPositionService exporterPositionService,
      final VariableReader variableReader,
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessInstanceReader processInstanceReader) {
    return new RdbmsService(
        executionQueue,
        exporterPositionService,
        processDefinitionReader,
        processInstanceReader,
        variableReader);
  }
}
