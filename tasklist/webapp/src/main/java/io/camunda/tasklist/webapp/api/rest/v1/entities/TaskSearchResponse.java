/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.tasklist.TaskState;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.StringJoiner;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.rule.Variable;
import org.antlr.runtime.RecognitionException;


public class TaskSearchResponse {
  @Schema(description = "The unique identifier of the task.")
  private String id;

  @Schema(description = "The name of the task.")
  private String name;

  @Schema(description = "User Task ID from the BPMN definition.")
  private String taskDefinitionId;

  @Schema(description = "The name of the process.")
  private String processName;

  @Schema(
      description = "When was the task created (renamed equivalent of `Task.creationTime` field).")
  private String creationDate;

  @Schema(
      description =
          "When was the task completed (renamed equivalent of `Task.completionTime` field).")
  private String completionDate;

  @Schema(description = "The username/id of who is assigned to the task.")
  private String assignee;

  @Schema(description = "The state of the task.")
  private TaskState taskState;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "Array of values to be copied into `TaskSearchRequest` to request for next or previous page of tasks."))
  private String[] sortValues;

  @Schema(description = "A flag to show that the task is first in the current filter.")
  private boolean isFirst;

  @Schema(description = "Reference to the task form.")
  private String formKey;

  @Schema(
      description =
          "Reference to the ID of a deployed form. If the form is not deployed, this property is null.")
  private String formId;

  @Schema(
      description =
          "Reference to the version of a deployed form. If the form is not deployed, this property is null.")
  private Long formVersion;

  @Schema(
      description =
          "Is the form embedded for this task? If there is no form, this property is null.")
  private Boolean isFormEmbedded;

  @Schema(
      description =
          "Reference to process definition (renamed equivalent of `Task.processDefinitionId` field).")
  private String processDefinitionKey;

  @Schema(
      description =
          "Reference to process instance id (renamed equivalent of `Task.processInstanceId` field).")
  private String processInstanceKey;

  @Schema(description = "The tenant ID associated with the task.")
  private String tenantId;

  @Schema(description = "The due date for the task.")
  private OffsetDateTime dueDate;

  @Schema(description = "The follow-up date for the task.")
  private OffsetDateTime followUpDate;

  @ArraySchema(arraySchema = @Schema(description = "The candidate groups for the task."))
  private String[] candidateGroups;

  @ArraySchema(arraySchema = @Schema(description = "The candidate users for the task."))
  private String[] candidateUsers;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "An array of the task's variables. Only variables specified in `TaskSearchRequest.includeVariables` are returned. Note that a variable's draft value is not returned in `TaskSearchResponse`."))
  @JsonIgnoreProperties("draft")
  private VariableSearchResponse[] variables;

  @Schema(description = "The context variable (from modeler) of the task.")
  private String context;

  private TaskImplementation implementation;

  @Schema(description = "The assigned priority of the task. Only for Zeebe User Tasks.")
  private int priority;

  @Schema(description = "The assigned precedence.")
  private Risk risk;

  public String getId() {
    return id;
  }

  public TaskSearchResponse setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public TaskSearchResponse setName(final String name) {
    this.name = name;
    return this;
  }

  public String getTaskDefinitionId() {
    return taskDefinitionId;
  }

  public TaskSearchResponse setTaskDefinitionId(final String taskDefinitionId) {
    this.taskDefinitionId = taskDefinitionId;
    return this;
  }

  public String getProcessName() {
    return processName;
  }

  public TaskSearchResponse setProcessName(final String processName) {
    this.processName = processName;
    return this;
  }

  public String getCreationDate() {
    return creationDate;
  }

  public TaskSearchResponse setCreationDate(final String creationDate) {
    this.creationDate = creationDate;
    return this;
  }

  public String getCompletionDate() {
    return completionDate;
  }

  public TaskSearchResponse setCompletionDate(final String completionDate) {
    this.completionDate = completionDate;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskSearchResponse setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public TaskState getTaskState() {
    return taskState;
  }

  public TaskSearchResponse setTaskState(final TaskState taskState) {
    this.taskState = taskState;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public TaskSearchResponse setSortValues(final String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public boolean getIsFirst() {
    return isFirst;
  }

  public TaskSearchResponse setIsFirst(final boolean first) {
    isFirst = first;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskSearchResponse setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskSearchResponse setFormId(final String formId) {
    this.formId = formId;
    return this;
  }

  public Long getFormVersion() {
    return formVersion;
  }

  public TaskSearchResponse setFormVersion(final Long formVersion) {
    this.formVersion = formVersion;
    return this;
  }

  public Boolean getIsFormEmbedded() {
    return isFormEmbedded;
  }

  public TaskSearchResponse setIsFormEmbedded(final Boolean isFormEmbedded) {
    this.isFormEmbedded = isFormEmbedded;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public TaskSearchResponse setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  public TaskSearchResponse setProcessInstanceKey(final String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public TaskSearchResponse setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskSearchResponse setDueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskSearchResponse setFollowUpDate(final OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskSearchResponse setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskSearchResponse setCandidateUsers(final String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public VariableSearchResponse[] getVariables() {
    return variables;
  }

  public TaskSearchResponse setVariables(final VariableSearchResponse[] variables) {
    this.variables = variables;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskSearchResponse setImplementation(final TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public String getContext() {
    return context;
  }

  public TaskSearchResponse setContext(final String context) {
    this.context = context;
    return this;
  }

  public int getPriority() {
    return priority;
  }

  public TaskSearchResponse setPriority(final int priority) {
    this.priority = priority;
    return this;
  }

  public Risk getRisk() throws RecognitionException {

    return new Risk(new Precedence(0.0), calculateTaskRisk(dueDate, priority));
  }



  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id,
            name,
            taskDefinitionId,
            processName,
            creationDate,
            completionDate,
            assignee,
            taskState,
            isFirst,
            formKey,
            formId,
            formVersion,
            isFormEmbedded,
            processDefinitionKey,
            processInstanceKey,
            tenantId,
            dueDate,
            followUpDate,
            implementation,
            context,
            priority);
    result = 31 * result + Arrays.hashCode(sortValues);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
    result = 31 * result + Arrays.hashCode(variables);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TaskSearchResponse that = (TaskSearchResponse) o;
    return isFirst == that.isFirst
        && implementation == that.implementation
        && Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(taskDefinitionId, that.taskDefinitionId)
        && Objects.equals(processName, that.processName)
        && Objects.equals(creationDate, that.creationDate)
        && Objects.equals(completionDate, that.completionDate)
        && Objects.equals(assignee, that.assignee)
        && Objects.equals(context, that.context)
        && taskState == that.taskState
        && Arrays.equals(sortValues, that.sortValues)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(formId, that.formId)
        && Objects.equals(formVersion, that.formVersion)
        && Objects.equals(isFormEmbedded, that.isFormEmbedded)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(dueDate, that.dueDate)
        && Objects.equals(followUpDate, that.followUpDate)
        && priority == that.priority
        && Arrays.equals(candidateGroups, that.candidateGroups)
        && Arrays.equals(candidateUsers, that.candidateUsers)
        && Arrays.equals(variables, that.variables);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskSearchResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("name='" + name + "'")
        .add("taskDefinitionId='" + taskDefinitionId + "'")
        .add("processName='" + processName + "'")
        .add("creationDate='" + creationDate + "'")
        .add("completionDate='" + completionDate + "'")
        .add("assignee='" + assignee + "'")
        .add("taskState=" + taskState)
        .add("implementation=" + implementation)
        .add("sortValues=" + Arrays.toString(sortValues))
        .add("isFirst=" + isFirst)
        .add("formKey='" + formKey + "'")
        .add("formId='" + formId + "'")
        .add("formVersion='" + formVersion + "'")
        .add("isFormEmbedded='" + isFormEmbedded + "'")
        .add("processDefinitionKey='" + processDefinitionKey + "'")
        .add("processInstanceKey='" + processInstanceKey + "'")
        .add("tenantId='" + tenantId + "'")
        .add("dueDate='" + dueDate + "'")
        .add("followUpDate='" + followUpDate + "'")
        .add("candidateGroups=" + Arrays.toString(candidateGroups))
        .add("candidateUsers=" + Arrays.toString(candidateUsers))
        .add("variables=" + Arrays.toString(variables))
        .add("taskContext='" + context + "'")
        .add("priority='" + priority + "'")
        .add("risk='" + risk + "'")
        .toString();
  }

  private String calculateTaskRisk(final OffsetDateTime dueDate, final int priority) throws RecognitionException {
    // Define the FCL rules as a string
    final String fclRules =
        "FUNCTION_BLOCK TaskPriority\n"
            + "\n"
            + "VAR_INPUT\n"
            + "    Due : REAL;\n"
            + "    Priority : REAL;\n"
            + "END_VAR\n"
            + "\n"
            + "VAR_OUTPUT\n"
            + "    Output : REAL;\n"
            + "END_VAR\n"
            + "\n"
            + "FUZZIFY Due\n"
            + "    TERM Short_Time := (0,1) (2,1) (5,0);\n"
            + "    TERM Medium := (2,0) (5,1) (8,0);\n"
            + "    TERM High := (5,0) (8,1) (10,1);\n"
            + "END_FUZZIFY\n"
            + "\n"
            + "FUZZIFY Priority\n"
            + "    TERM Low := (0,1) (20,1) (50,0);\n"
            + "    TERM Medium := (20,0) (50,1) (80,0);\n"
            + "    TERM High := (50,0) (80,1) (100,1);\n"
            + "END_FUZZIFY\n"
            + "\n"
            + "DEFUZZIFY Output\n"
            + "    TERM Very_Low := (0,1) (2,1) (4,0);\n"
            + "    TERM Low := (2,0) (4,1) (6,0);\n"
            + "    TERM Medium := (4,0) (6,1) (8,0);\n"
            + "    TERM High := (6,0) (8,1) (10,0);\n"
            + "    TERM Urgent := (8,0) (10,1) (10,1);\n"
            + "    METHOD : COG;\n"
            + "END_DEFUZZIFY\n"
            + "\n"
            + "RULEBLOCK Rules\n"
            + "    AND : MIN;\n"
            + "    ACT : MIN;\n"
            + "    ACCU : MAX;\n"
            + "\n"
            + "    RULE 1 : IF Due IS Short_Time AND Priority IS High THEN Output IS Urgent;\n"
            + "    RULE 2 : IF Due IS Short_Time AND Priority IS Medium THEN Output IS High;\n"
            + "    RULE 3 : IF Due IS Medium AND Priority IS High THEN Output IS Medium;\n"
            + "    RULE 4 : IF Due IS Medium AND Priority IS Medium THEN Output IS Low;\n"
            + "    RULE 5 : IF Due IS High AND Priority IS Low THEN Output IS Very_Low;\n"
            + "    RULE 6 : IF Due IS High AND Priority IS Medium THEN Output IS Low;\n"
            + "END_RULEBLOCK\n"
            + "\n"
            + "END_FUNCTION_BLOCK\n"; // This was missing in the previous version

    // Create FIS from string
    final FIS fis = FIS.createFromString(fclRules, true);

    // Check if successfully created
    if (fis == null) {
      System.err.println("Error: Could not create FIS from the provided string.");
      return "Error";
    }

    if (dueDate != null) {
      final long daysUntilDue =
          ChronoUnit.DAYS.between(OffsetDateTime.now(), dueDate); // Example: DueDate = 3

      if (dueDate.isAfter(OffsetDateTime.now())) {
        fis.setVariable("Due", daysUntilDue);
      } else {
        fis.setVariable("Due", 0);
      }
    } else {
      fis.setVariable("Due", 10);
    }
    // Example: Priority = 80
    fis.setVariable("Priority", priority); // Example: Priority = 80

    // Evaluate the fuzzy system

    fis.evaluate();

    // Get the crisp output
    final Variable output = fis.getVariable("Output");

    // transform output to double
    final double outputValue = output.getValue();

    // Print results
    // Classify my task
    if (outputValue >= 0 && outputValue < 2) {
      return "VERYLOW";
    } else if (outputValue >= 2 && outputValue < 4) {
      return "LOW";
    } else if (outputValue >= 4 && outputValue < 6) {
      return "MEDIUM";
    } else if (outputValue >= 6 && outputValue < 8) {
      return "HIGH";
    } else {
      return "URGENT";
    }

  }

}
