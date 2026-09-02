/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.store.ZeebeStore;
import io.camunda.operate.zeebe.ImportValueType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import org.junit.Test;

public class ImportJobRefreshZeebeIndicesTest {

  @Test
  public void refreshZeebeIndicesBuildsPatternFromZeebeStorePrefix() throws Exception {
    final ZeebeStore zeebeStore = mock(ZeebeStore.class);
    when(zeebeStore.getZeebeIndexPrefix()).thenReturn("custom-os-prefix");

    final ImportBatch importBatch =
        new ImportBatch(1, ImportValueType.PROCESS_INSTANCE, new ArrayList<>(), "some_name");
    final ImportJob importJob = new ImportJob(importBatch, new ImportPositionEntity());
    setField(importJob, "zeebeStore", zeebeStore);

    importJob.refreshZeebeIndices();

    verify(zeebeStore).getZeebeIndexPrefix();
    verify(zeebeStore).refreshIndex(eq("custom-os-prefix*process-instance*"));
  }

  private static void setField(final Object target, final String fieldName, final Object value)
      throws Exception {
    final Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
