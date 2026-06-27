/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.WaitStateDbModel;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.function.Function;

public final class WaitStateFixtures extends CommonFixtures {

  private WaitStateFixtures() {}

  public static WaitStateDbModel createRandomized(
      final Function<WaitStateDbModel.Builder, WaitStateDbModel.Builder> builderFunction) {
    final var key = nextKey();
    final var builder =
        new WaitStateDbModel.Builder()
            .waitStateKey(key)
            .rootProcessInstanceKey(nextKey())
            .processInstanceKey(nextKey())
            .elementInstanceKey(nextKey())
            .elementId("task-" + key)
            .elementType(BpmnElementType.SERVICE_TASK.name())
            .waitStateType("JOB")
            .processDefinitionId("process-" + generateRandomString(10))
            .details("{\"jobKey\":" + key + ",\"jobType\":\"payment\",\"retries\":3}")
            .tenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .partitionId(0);
    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomWaitStates(
      final RdbmsWriters rdbmsWriters,
      final Function<WaitStateDbModel.Builder, WaitStateDbModel.Builder> builderFunction) {
    createAndSaveRandomWaitStates(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomWaitStates(
      final RdbmsWriters rdbmsWriters,
      final int count,
      final Function<WaitStateDbModel.Builder, WaitStateDbModel.Builder> builderFunction) {
    for (int i = 0; i < count; i++) {
      rdbmsWriters.getWaitStateWriter().create(createRandomized(builderFunction));
    }
    rdbmsWriters.flush();
  }
}
