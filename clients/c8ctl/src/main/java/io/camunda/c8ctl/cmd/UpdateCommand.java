/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl.cmd;

import io.camunda.c8ctl.converters.ListConverter;
import io.camunda.c8ctl.mixin.ClientMixin;
import io.camunda.c8ctl.mixin.OutputMixin;
import io.camunda.client.api.response.UpdateJobResponse;
import io.camunda.client.api.response.UpdateUserTaskResponse;
import io.camunda.client.protocol.rest.JobChangeset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "update",
    description = "Updates a resource",
    subcommands = {})
public class UpdateCommand {

  @Command(name = "userTask", description = "Updates a user task defined by the user task key")
  public static class UserTaskCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<user_task_key>",
        description = "The key of the user task",
        type = Long.class)
    private long userTaskKey;

    @Option(
        names = {"--action"},
        paramLabel = "<action>",
        description = "The action to update the user task with",
        defaultValue = "")
    private String action;

    @Option(
        names = {"--dueDate"},
        paramLabel = "<due_date>",
        description = "Specify the due date of the user task")
    private String dueDate;

    @Option(
        names = {"--followUpDate"},
        paramLabel = "<follow_up_date>",
        description = "Specify the follow-up date of the user task")
    private String followUpDate;

    @Option(
        names = {"--priority"},
        paramLabel = "<priority>",
        description = "Specify the priority of the user task")
    private int priority;

    @Option(
        names = {"--candidateUsers"},
        paramLabel = "<candidate_users>",
        description = "Specify the candidate users of the user task as a comma-separated list",
        defaultValue = "",
        converter = ListConverter.class)
    private List<String> candidateUsers;

    @Option(
        names = {"--candidateGroups"},
        paramLabel = "<candidate_groups>",
        description = "Specify the candidate groups of the user task as a comma-separated list",
        defaultValue = "",
        converter = ListConverter.class)
    private List<String> candidateGroups;

    @Option(
        names = {"--clearDueDate"},
        description = "Clear the due date of the user task",
        defaultValue = "false",
        fallbackValue = "true",
        type = Boolean.class)
    private boolean clearDueDate;

    @Option(
        names = {"--clearFollowUpDate"},
        description = "Clear the follow-up date of the user task",
        defaultValue = "false",
        fallbackValue = "true",
        type = Boolean.class)
    private boolean clearFollowUpDate;

    @Option(
        names = {"--clearCandidateUsers"},
        description = "Clear the candidate users of the user task",
        defaultValue = "false",
        fallbackValue = "true",
        type = Boolean.class)
    private boolean clearCandidateUsers;

    @Option(
        names = {"--clearCandidateGroups"},
        description = "Clear the candidate groups of the user task",
        defaultValue = "false",
        fallbackValue = "true",
        type = Boolean.class)
    private boolean clearCandidateGroups;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var command = client.newUserTaskUpdateCommand(userTaskKey);

        if (dueDate != null && !dueDate.isEmpty()) {
          command.dueDate(dueDate);
        }

        if (clearDueDate) {
          command.clearDueDate();
        }

        if (followUpDate != null && !followUpDate.isEmpty()) {
          command.followUpDate(followUpDate);
        }

        if (clearFollowUpDate) {
          command.clearFollowUpDate();
        }

        if (!candidateUsers.isEmpty()) {
          command.candidateUsers(candidateUsers);
        }

        if (clearCandidateUsers) {
          command.clearCandidateUsers();
        }

        if (!candidateGroups.isEmpty()) {
          command.candidateGroups(candidateGroups);
        }

        if (clearCandidateGroups) {
          command.clearCandidateGroups();
        }

        if (priority != 0) {
          command.priority(priority);
        }

        if (action != null && !action.isEmpty()) {
          command.action(action);
        }

        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, UpdateUserTaskResponse.class);
      }

      return ExitCode.OK;
    }

    @Command(name = "job", description = "Updates a job defined by the job key")
    public static class JobCommand implements Callable<Integer> {

      @Mixin private ClientMixin clientMixin;
      @Mixin private OutputMixin outputMixin;

      @Parameters(
          paramLabel = "<job_key>",
          description = "The key of the job to update",
          type = Long.class)
      private long jobKey;

      @Option(
          names = {"--retries"},
          paramLabel = "<retries>",
          description = "The number of retries for the job",
          type = Integer.class)
      private Integer retries;

      @Option(
          names = {"--timeout"},
          paramLabel = "<timeout>",
          description = "The timeout for the job in milliseconds",
          type = Long.class)
      private Long timeout;

      @Override
      public Integer call() throws Exception {
        try (final var client = clientMixin.client()) {
          final var command = client.newUpdateJobCommand(jobKey);

          final var changeset = new JobChangeset();
          if (retries != null && retries >= 0) {
            changeset.retries(retries);
          }

          if (timeout != null && timeout >= 0) {
            changeset.timeout(timeout);
          }

          final var response = command.update(changeset).send().join(30, TimeUnit.SECONDS);
          outputMixin.formatter().write(response, UpdateJobResponse.class);
        }

        return ExitCode.OK;
      }
    }
  }
}
