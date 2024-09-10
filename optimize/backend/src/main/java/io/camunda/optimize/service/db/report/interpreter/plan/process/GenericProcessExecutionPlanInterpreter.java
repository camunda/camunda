/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.plan.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_DURATION_BY_VARIABLE_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_DURATION_BY_VARIABLE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_END_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_END_DATE_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_END_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_START_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_START_DATE_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_START_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_DURATION_GROUP_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_BY_VARIABLE_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_END_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_END_DATE_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_END_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_START_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_START_DATE_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_START_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.FLOW_NODE_FREQUENCY_GROUP_BY_VARIABLE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.INCIDENT_DURATION_GROUP_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.INCIDENT_DURATION_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.INCIDENT_FREQUENCY_GROUP_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.INCIDENT_FREQUENCY_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_GROUP_BY_END_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_GROUP_BY_END_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_GROUP_BY_END_DATE_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_GROUP_BY_NONE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_GROUP_BY_START_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_GROUP_BY_START_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_GROUP_BY_START_DATE_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_GROUP_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_GROUP_BY_VARIABLE_BY_END_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_GROUP_BY_VARIABLE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_GROUP_BY_VARIABLE_BY_START_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_END_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_END_DATE_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_START_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_START_DATE_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_VARIABLE_BY_END_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_VARIABLE_BY_START_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_DURATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_DURATION_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_END_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_END_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_END_DATE_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_NONE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_RUNNING_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_RUNNING_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_START_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_START_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_START_DATE_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_VARIABLE_BY_END_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_VARIABLE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_FREQUENCY_GROUP_BY_VARIABLE_BY_START_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_PERCENTAGE_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_ASSIGNEE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_ASSIGNEE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_ASSIGNEE_BY_TASK;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_CANDIDATE_GROUP_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_CANDIDATE_GROUP_BY_TASK;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_TASK;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_TASK_GROUP_BY_ASSIGNEE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_TASK_GROUP_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_TASK_GROUP_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_ASSIGNEE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_TASK;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_ASSIGNEE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_TASK;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE_DISTRIBUTE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE_DISTRIBUTE_BY_TASK;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_DISTRIBUTE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_DISTRIBUTE_BY_TASK;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_TASK;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_TASK_BY_ASSIGNEE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_TASK_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_TASK_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION_BY_USER_TASK;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE_BY_ASSIGNEE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE_BY_TASK;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_ASSIGNEE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_PROCESS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_TASK;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_VARIABLE_GROUP_BY_NONE;

import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import java.util.Set;

public interface GenericProcessExecutionPlanInterpreter extends ProcessExecutionPlanInterpreter {
  default Set<ProcessExecutionPlan> getSupportedExecutionPlans() {
    return Set.of(
        FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_END_DATE_BY_FLOW_NODE,
        FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_START_DATE_BY_FLOW_NODE,
        FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_END_DATE,
        FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_START_DATE,
        FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_END_DATE_BY_PROCESS,
        FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_START_DATE_BY_PROCESS,
        FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_BY_PROCESS,
        FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE,
        FLOW_NODE_DURATION_BY_VARIABLE_BY_FLOW_NODE,
        FLOW_NODE_DURATION_GROUP_BY_VARIABLE,
        FLOW_NODE_DURATION_BY_VARIABLE_BY_PROCESS,
        FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_END_DATE_BY_FLOW_NODE,
        FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_START_DATE_BY_FLOW_NODE,
        FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_END_DATE,
        FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_START_DATE,
        FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_END_DATE_BY_PROCESS,
        FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_START_DATE_BY_PROCESS,
        FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE,
        FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION_BY_PROCESS,
        FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION,
        FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_BY_PROCESS,
        FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE,
        FLOW_NODE_FREQUENCY_BY_VARIABLE_BY_FLOW_NODE,
        FLOW_NODE_FREQUENCY_GROUP_BY_VARIABLE_BY_PROCESS,
        FLOW_NODE_FREQUENCY_GROUP_BY_VARIABLE,
        INCIDENT_DURATION_GROUP_BY_FLOW_NODE,
        INCIDENT_DURATION_GROUP_BY_NONE,
        INCIDENT_FREQUENCY_GROUP_BY_FLOW_NODE,
        INCIDENT_FREQUENCY_GROUP_BY_NONE,
        PROCESS_INSTANCE_DURATION_GROUP_BY_END_DATE,
        PROCESS_INSTANCE_DURATION_GROUP_BY_START_DATE,
        PROCESS_INSTANCE_DURATION_GROUP_BY_END_DATE_BY_PROCESS,
        PROCESS_INSTANCE_DURATION_GROUP_BY_START_DATE_BY_PROCESS,
        PROCESS_INSTANCE_DURATION_GROUP_BY_END_DATE_BY_VARIABLE,
        PROCESS_INSTANCE_DURATION_GROUP_BY_START_DATE_BY_VARIABLE,
        PROCESS_INSTANCE_DURATION_GROUP_BY_NONE_BY_PROCESS,
        PROCESS_INSTANCE_DURATION_GROUP_BY_NONE,
        PROCESS_INSTANCE_DURATION_GROUP_BY_VARIABLE_BY_END_DATE,
        PROCESS_INSTANCE_DURATION_GROUP_BY_VARIABLE_BY_START_DATE,
        PROCESS_INSTANCE_DURATION_GROUP_BY_VARIABLE,
        PROCESS_INSTANCE_DURATION_GROUP_BY_VARIABLE_BY_PROCESS,
        PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_END_DATE,
        PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_START_DATE,
        PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_END_DATE_BY_VARIABLE,
        PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_START_DATE_BY_VARIABLE,
        PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_NONE,
        PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_VARIABLE_BY_END_DATE,
        PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_VARIABLE_BY_START_DATE,
        PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_VARIABLE,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_END_DATE,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_RUNNING_DATE,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_START_DATE,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_END_DATE_BY_PROCESS,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_RUNNING_DATE_BY_PROCESS,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_START_DATE_BY_PROCESS,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_END_DATE_BY_VARIABLE,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_START_DATE_BY_VARIABLE,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_DURATION_BY_PROCESS,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_DURATION,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_NONE_BY_PROCESS,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_NONE,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_VARIABLE_BY_END_DATE,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_VARIABLE_BY_START_DATE,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_VARIABLE_BY_PROCESS,
        PROCESS_INSTANCE_FREQUENCY_GROUP_BY_VARIABLE,
        PROCESS_INSTANCE_PERCENTAGE_GROUP_BY_NONE,
        PROCESS_USER_TASK_DURATION_GROUP_BY_ASSIGNEE,
        PROCESS_USER_TASK_DURATION_GROUP_BY_ASSIGNEE_BY_PROCESS,
        PROCESS_USER_TASK_DURATION_GROUP_BY_ASSIGNEE_BY_TASK,
        PROCESS_USER_TASK_DURATION_GROUP_BY_CANDIDATE_GROUP,
        PROCESS_USER_TASK_DURATION_GROUP_BY_CANDIDATE_GROUP_BY_PROCESS,
        PROCESS_USER_TASK_DURATION_GROUP_BY_CANDIDATE_GROUP_BY_TASK,
        PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_ASSIGNEE,
        PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_ASSIGNEE,
        PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_CANDIDATE_GROUP,
        PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_CANDIDATE_GROUP,
        PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE,
        PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE,
        PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_PROCESS,
        PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_PROCESS,
        PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_TASK,
        PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_TASK,
        PROCESS_USER_TASK_DURATION_GROUP_BY_TASK_GROUP_BY_ASSIGNEE,
        PROCESS_USER_TASK_DURATION_GROUP_BY_TASK_GROUP_BY_CANDIDATE_GROUP,
        PROCESS_USER_TASK_DURATION_GROUP_BY_TASK_GROUP_BY_PROCESS,
        PROCESS_USER_TASK_DURATION_GROUP_BY_TASK,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE_DISTRIBUTE_BY_PROCESS,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE_DISTRIBUTE_BY_TASK,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_DISTRIBUTE_BY_PROCESS,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_DISTRIBUTE_BY_TASK,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_GROUP,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE_BY_ASSIGNEE,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_ASSIGNEE,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE_BY_CANDIDATE_GROUP,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_CANDIDATE_GROUP,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE_BY_PROCESS,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_PROCESS,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE_BY_TASK,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_TASK,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION_BY_PROCESS,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION_BY_USER_TASK,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_TASK_BY_ASSIGNEE,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_TASK_BY_CANDIDATE_GROUP,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_TASK_BY_PROCESS,
        PROCESS_USER_TASK_FREQUENCY_GROUP_BY_TASK,
        PROCESS_VARIABLE_GROUP_BY_NONE);
  }
}
