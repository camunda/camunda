/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import java.util.List;
import java.util.function.Function;

public final class JobFixtures extends CommonFixtures {

  private JobFixtures() {}

  public static JobDbModel createRandomized(
      final Function<JobDbModel.Builder, JobDbModel.Builder> builderFunction) {
    final var builder =
        new JobDbModel.Builder()
            .jobKey(nextKey())
            .processDefinitionKey(nextKey())
            .processDefinitionId("job-" + generateRandomString(20))
            .processInstanceKey(nextKey())
            .rootProcessInstanceKey(nextKey())
            .jobKey(nextKey())
            .state(randomEnum(JobState.class))
            .errorMessage("error-" + generateRandomString(20))
            .tenantId("tenant-" + generateRandomString(20))
            .deadline(NOW)
            .elementId("element-" + generateRandomString(20))
            .elementInstanceKey(nextKey())
            .worker("worker-" + generateRandomString(20))
            .endTime(NOW)
            .isDenied(false)
            .retries(1)
            .errorCode("error-code-" + generateRandomString(20))
            .deniedReason("denied-reason-" + generateRandomString(20))
            .listenerEventType(randomEnum(ListenerEventType.class))
            .creationTime(NOW)
            .lastUpdateTime(NOW)
            .kind(randomEnum(JobKind.class));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomJobs(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomJobs(rdbmsWriters, b -> b);
  }

  public static void createAndSaveRandomJobs(
      final RdbmsWriters rdbmsWriters,
      final Function<JobDbModel.Builder, JobDbModel.Builder> builderFunction) {
    createAndSaveRandomJobs(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomJobs(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<JobDbModel.Builder, JobDbModel.Builder> builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters.getJobWriter().create(JobFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static JobDbModel createAndSaveJob(
      final RdbmsWriters rdbmsWriters,
      final Function<JobDbModel.Builder, JobDbModel.Builder> builderFunction) {
    final JobDbModel randomized = createRandomized(builderFunction);
    createAndSaveJobs(rdbmsWriters, List.of(randomized));
    return randomized;
  }

  public static void createAndSaveJob(final RdbmsWriters rdbmsWriters, final JobDbModel job) {
    createAndSaveJobs(rdbmsWriters, List.of(job));
  }

  public static void createAndSaveJobs(
      final RdbmsWriters rdbmsWriters, final List<JobDbModel> jobList) {
    for (final JobDbModel job : jobList) {
      rdbmsWriters.getJobWriter().create(job);
    }
    rdbmsWriters.flush();
  }
}
