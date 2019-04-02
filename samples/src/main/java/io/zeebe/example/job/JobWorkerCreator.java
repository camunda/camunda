/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.example.job;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.api.subscription.JobWorker;
import java.time.Duration;
import java.util.Scanner;

public class JobWorkerCreator {
  public static void main(final String[] args) {
    final String broker = "127.0.0.1:26500";

    final String jobType = "foo";

    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder().brokerContactPoint(broker);

    try (ZeebeClient client = builder.build()) {

      System.out.println("Opening job worker.");

      final JobWorker workerRegistration =
          client
              .newWorker()
              .jobType(jobType)
              .handler(new ExampleJobHandler())
              .timeout(Duration.ofSeconds(10))
              .open();

      System.out.println("Job worker opened and receiving jobs.");

      // call workerRegistration.close() to close it

      // run until System.in receives exit command
      waitUntilSystemInput("exit");
    }
  }

  private static class ExampleJobHandler implements JobHandler {
    @Override
    public void handle(final JobClient client, final ActivatedJob job) {
      // here: business logic that is executed with every job
      System.out.println(
          String.format(
              "[type: %s, key: %s, lockExpirationTime: %s]\n[headers: %s]\n[variables: %s]\n===",
              job.getType(),
              job.getKey(),
              job.getDeadline().toString(),
              job.getHeaders(),
              job.getVariables()));

      client.newCompleteCommand(job.getKey()).send().join();
    }
  }

  private static void waitUntilSystemInput(final String exitCode) {
    try (Scanner scanner = new Scanner(System.in)) {
      while (scanner.hasNextLine()) {
        final String nextLine = scanner.nextLine();
        if (nextLine.contains(exitCode)) {
          return;
        }
      }
    }
  }
}
