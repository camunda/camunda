/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceWithResultCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.zbctl.cmd.CreateCommand.InstanceCommand;
import io.camunda.zeebe.zbctl.cmd.CreateCommand.WorkerCommand;
import io.camunda.zeebe.zbctl.converters.DurationConverter;
import io.camunda.zeebe.zbctl.converters.JsonInputConverter;
import io.camunda.zeebe.zbctl.converters.JsonInputConverter.JsonInput;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.ShutdownSignalBarrier;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "create",
    description = "Create resources",
    subcommands = {InstanceCommand.class, WorkerCommand.class})
public class CreateCommand {

  @Command(
      name = "instance",
      description = "Creates a new process instance defined by the process ID or process key")
  public static class InstanceCommand implements Callable<Integer> {
    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<processId or process key>",
        description = "Either the BPMN process ID or the process key")
    private String processId;

    @Option(
        names = {"--version"},
        paramLabel = "<version>",
        description = "Specify version of process which should be executed (use -1 for the latest)",
        defaultValue = "-1")
    private int version;

    @Option(
        names = {"--variables"},
        paramLabel = "<variables>",
        description = "Specify message variables as JSON string or path to JSON file",
        defaultValue = "{}",
        converter = JsonInputConverter.class)
    private JsonInput variables;

    @Option(
        names = {"--withResult"},
        paramLabel = "<withResult>",
        description =
            "Specify to await result of process, optional a list of variable names can be provided to limit the returned variables")
    private String[] withResult;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        var command = prepareCommand(client);

        try (final var variablesInput = variables.open()) {
          command = command.variables(variablesInput);
        }

        if (withResult != null) {
          sendCommandWithResult(command);
        } else {
          final var response = command.send().join(30, TimeUnit.SECONDS);
          outputMixin.formatter().write(response, ProcessInstanceEvent.class);
        }
      }

      return ExitCode.OK;
    }

    private void sendCommandWithResult(final CreateProcessInstanceCommandStep3 command)
        throws IOException {
      var commandWithResult = command.withResult();
      commandWithResult =
          Arrays.stream(withResult)
              .reduce(
                  commandWithResult,
                  CreateProcessInstanceWithResultCommandStep1::fetchVariables,
                  (c1, c2) -> c1);
      final var response = commandWithResult.send().join(30, TimeUnit.SECONDS);
      outputMixin.formatter().write(response, ProcessInstanceResult.class);
    }

    private CreateProcessInstanceCommandStep3 prepareCommand(final ZeebeClient client) {
      final var command = client.newCreateInstanceCommand();

      try {
        return command.processDefinitionKey(Integer.parseInt(processId));
      } catch (final NumberFormatException e) {
        return command.bpmnProcessId(processId).version(version);
      }
    }
  }

  @Command(name = "worker", description = "Create a job worker")
  public static class WorkerCommand implements Callable<Integer> {
    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(paramLabel = "<type>", description = "The type of jobs to activate and handle")
    private String type;

    @Option(
        names = {"--handler"},
        paramLabel = "<handler>",
        description =
            "Specify handler to invoke for each job; if omitted, jobs are printed out but not completed")
    private String handler;

    @Option(
        names = {"--maxJobsActive"},
        paramLabel = "<maxJobsActive>",
        description = "Specify the maximum number of jobs to be handled concurrently",
        defaultValue = "32")
    private int maxJobsActive;

    @Option(
        names = {"--name"},
        paramLabel = "<name>",
        description = "Specify the worker's name",
        defaultValue = "zbctl")
    private String name;

    @Option(
        names = {"--pollInterval"},
        paramLabel = "<pollInterval>",
        description =
            "Specify the maximal interval between polling for new jobs. Example values: 300ms, 50s or 1m",
        defaultValue = "100ms",
        converter = DurationConverter.class)
    private Duration pollInterval;

    @Option(
        names = {"--timeout"},
        paramLabel = "<timeout>",
        description =
            "Specify the duration no other worker should work on job activated by this worker. Example values: 300ms, 50s or 1m",
        defaultValue = "5m",
        converter = DurationConverter.class)
    private Duration timeout;

    @Option(
        names = {"--variables"},
        paramLabel = "<variables>",
        description = "Specify an optional list of variable names to limit the returned variables")
    private String[] variables;

    @Override
    public Integer call() {
      try (final var client = clientMixin.client(this::configureWorker)) {
        var builder =
            client
                .newWorker()
                .jobType(type)
                .handler(createJobHandler())
                .pollInterval(pollInterval)
                .maxJobsActive(maxJobsActive)
                .name(name)
                .timeout(timeout)
                .streamEnabled(true);

        if (variables != null) {
          builder = builder.fetchVariables(variables);
        }

        try (final var ignored = builder.open()) {
          final var barrier = new ShutdownSignalBarrier();
          barrier.await();
        }
      }

      return ExitCode.OK;
    }

    private ZeebeClientBuilder configureWorker(final ZeebeClientBuilder builder) {
      return builder.jobWorkerExecutor(
          Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory()), true);
    }

    private JobHandler createJobHandler() {
      if (handler == null || handler.isBlank()) {
        return defaultJobHandler();
      }

      final var builder = new ProcessBuilder().inheritIO();
      return (ignored, job) -> {
        builder
            .command(handler, outputMixin.formatter().serialize(job, ActivatedJob.class))
            .start();
      };
    }

    private JobHandler defaultJobHandler() {
      return (client, job) -> {
        outputMixin.formatter().write(job, ActivatedJob.class);
        client.newCompleteCommand(job).send().join();
      };
    }
  }
}
