/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_6.processors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.ZeebeElasticsearchProperties;
import io.camunda.operate.property.ZeebeOpensearchProperties;
import io.camunda.operate.util.SoftHashMap;
import io.camunda.operate.zeebe.PartitionHolder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListViewZeebeRecordProcessorTest {

  @Mock private OperateProperties operateProperties;
  @Mock private PartitionHolder partitionHolder;
  @Mock private DatabaseInfo databaseInfo;
  @InjectMocks private ListViewZeebeRecordProcessor processor;

  // Deliberately NOT stubbing operateProperties.getElasticsearch()/getOpensearch(): if the
  // fix regresses back to reading the Operate-storage batch size instead of the Zeebe-record
  // read side one, the mock returns null and the call below throws NullPointerException.

  @Test
  void shouldSizeTreePathCacheFromZeebeElasticsearchBatchSizeWhenOnElasticsearch()
      throws Exception {
    // given
    final ZeebeElasticsearchProperties zeebeElasticsearch = new ZeebeElasticsearchProperties();
    zeebeElasticsearch.setBatchSize(2000);
    when(databaseInfo.isOpensearchDb()).thenReturn(false);
    when(operateProperties.getZeebeElasticsearch()).thenReturn(zeebeElasticsearch);
    when(partitionHolder.getPartitionIds()).thenReturn(List.of(1, 2, 3, 4));

    // when
    final SoftHashMap<?, ?> treePathCache = invokePrivateCacheGetter("getTreePathCache");

    // then
    assertThat(retentionSizeOf(treePathCache)).isEqualTo(2000 * 4);
  }

  @Test
  void shouldSizeCallActivityIdCacheFromZeebeOpensearchBatchSizeWhenOnOpensearch()
      throws Exception {
    // given
    final ZeebeOpensearchProperties zeebeOpensearch = new ZeebeOpensearchProperties();
    zeebeOpensearch.setBatchSize(500);
    when(databaseInfo.isOpensearchDb()).thenReturn(true);
    when(operateProperties.getZeebeOpensearch()).thenReturn(zeebeOpensearch);
    when(partitionHolder.getPartitionIds()).thenReturn(List.of(1, 2));

    // when
    final SoftHashMap<?, ?> callActivityIdCache =
        invokePrivateCacheGetter("getCallActivityIdCache");

    // then
    assertThat(retentionSizeOf(callActivityIdCache)).isEqualTo(500 * 2);
  }

  private SoftHashMap<?, ?> invokePrivateCacheGetter(final String methodName) throws Exception {
    final Method method = ListViewZeebeRecordProcessor.class.getDeclaredMethod(methodName);
    method.setAccessible(true);
    return (SoftHashMap<?, ?>) method.invoke(processor);
  }

  private int retentionSizeOf(final SoftHashMap<?, ?> cache) throws Exception {
    final Field field = SoftHashMap.class.getDeclaredField("retentionSize");
    field.setAccessible(true);
    return (int) field.get(cache);
  }
}
