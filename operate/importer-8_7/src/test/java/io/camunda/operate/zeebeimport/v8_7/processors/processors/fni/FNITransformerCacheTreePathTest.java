/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_7.processors.processors.fni;

import static io.camunda.operate.zeebeimport.v8_7.processors.processors.fni.FNITransformerTest.createZeebeRecord;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.camunda.operate.zeebeimport.cache.FNITreePathCacheCompositeKey;
import io.camunda.operate.zeebeimport.cache.TreePathCache;
import io.camunda.operate.zeebeimport.processors.fni.FNITransformer;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.Mockito;

public class FNITransformerCacheTreePathTest {

  private FNITransformer fniTransformer;
  private TreePathCache mockTreePathCache;

  @BeforeEach
  public void setup() {
    mockTreePathCache = Mockito.mock(TreePathCache.class);
    when(mockTreePathCache.resolveParentTreePath(any()))
        .thenAnswer(
            invocationOnMock -> {
              final FNITreePathCacheCompositeKey compositeKey = invocationOnMock.getArgument(0);
              return String.format(
                  "%d/%d", compositeKey.processInstanceKey(), compositeKey.flowScopeKey());
            });
    fniTransformer = new FNITransformer(mockTreePathCache);
  }

  @ParameterizedTest
  @EnumSource(
      value = BpmnElementType.class,
      names = {
        "SUB_PROCESS",
        "PROCESS",
        "EVENT_SUB_PROCESS",
        "MULTI_INSTANCE_BODY",
        "AD_HOC_SUB_PROCESS"
      },
      mode = Mode.INCLUDE)
  void shouldCacheContainerFNI(final BpmnElementType elementType) {
    // given
    final var time = System.currentTimeMillis();
    final var record =
        createZeebeRecord(time, ProcessInstanceIntent.ELEMENT_ACTIVATING, elementType);

    // when
    fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    Mockito.verify(mockTreePathCache, times(1)).cacheTreePath(any(), eq("1/3/4"));
  }

  @ParameterizedTest
  @EnumSource(
      value = BpmnElementType.class,
      names = {
        "SUB_PROCESS",
        "PROCESS",
        "EVENT_SUB_PROCESS",
        "MULTI_INSTANCE_BODY",
        "AD_HOC_SUB_PROCESS"
      },
      mode = Mode.EXCLUDE)
  void shouldNotCacheNonContainerFNI(final BpmnElementType elementType) {
    // given
    final var time = System.currentTimeMillis();
    final var record =
        createZeebeRecord(time, ProcessInstanceIntent.ELEMENT_ACTIVATING, elementType);

    // when
    fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    Mockito.verify(mockTreePathCache, times(0)).cacheTreePath(any(), any());
  }
}
