/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import io.zeebe.tasklist.entities.ActivityInstanceEntity;
import io.zeebe.tasklist.entities.ActivityState;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.util.CollectionUtil;
import io.zeebe.tasklist.webapp.es.reader.ActivityInstanceReader;
import io.zeebe.tasklist.webapp.rest.exception.NotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@Configuration
@ConditionalOnProperty(prefix = TasklistProperties.PREFIX, name = "webappEnabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchChecks {

//  @Autowired
//  private WorkflowReader workflowReader;

  /**
   * Checks whether the workflow of given args[0] workflowKey (Long) is deployed.
   * @return
   */
//  @Bean(name = "workflowIsDeployedCheck")
//  public Predicate<Object[]> getWorkflowIsDeployedCheck() {
//    return objects -> {
//      assertThat(objects).hasSize(1);
//      assertThat(objects[0]).isInstanceOf(Long.class);
//      Long workflowKey = (Long)objects[0];
//      try {
//        final WorkflowEntity workflow = workflowReader.getWorkflow(workflowKey);
//        return workflow != null;
//      } catch (NotFoundException ex) {
//        return false;
//      }
//    };
//  }


}
