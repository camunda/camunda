/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.opensearch;

import static io.camunda.operate.schema.SchemaManager.OPERATE_DELETE_ARCHIVED_INDICES;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.property.ArchiverProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchISMOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class OpensearchSchemaManagerTest {

  @Mock OperateProperties operateProperties;
  @Mock RichOpenSearchClient richOpenSearchClient;
  @Mock List<TemplateDescriptor> templateDescriptors;
  @Mock List<IndexDescriptor> indexDescriptors;

  @InjectMocks OpensearchSchemaManager underTest;

  @Test
  public void testCheckAndUpdateIndices() {
    final IndexDescriptor indexDescriptor1 = mock(IndexDescriptor.class);
    final IndexDescriptor indexDescriptor2 = mock(IndexDescriptor.class);
    final IndexDescriptor indexDescriptor3 = mock(IndexDescriptor.class);
    when(indexDescriptor1.getDerivedIndexNamePattern()).thenReturn("index1*");
    when(indexDescriptor2.getDerivedIndexNamePattern()).thenReturn("index2*");
    when(indexDescriptor3.getDerivedIndexNamePattern()).thenReturn("index3*");

    final TemplateDescriptor templateDescriptor1 = mock(TemplateDescriptor.class);
    when(templateDescriptor1.getDerivedIndexNamePattern()).thenReturn("template1*");
    final TemplateDescriptor templateDescriptor2 = mock(TemplateDescriptor.class);
    when(templateDescriptor2.getDerivedIndexNamePattern()).thenReturn("template2*");

    ReflectionTestUtils.setField(
        underTest,
        "indexDescriptors",
        Arrays.asList(indexDescriptor1, indexDescriptor2, indexDescriptor3));

    ReflectionTestUtils.setField(
        underTest, "templateDescriptors", Arrays.asList(templateDescriptor1, templateDescriptor2));

    final ArchiverProperties archiverProperties = mock(ArchiverProperties.class);
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(archiverProperties.isIlmEnabled())
        .thenReturn(true)
        .thenReturn(false)
        .thenReturn(false)
        .thenReturn(true)
        .thenReturn(false);

    final OpenSearchISMOperations openSearchISMOperations = mock(OpenSearchISMOperations.class);
    when(richOpenSearchClient.ism()).thenReturn(openSearchISMOperations);
    when(openSearchISMOperations.addPolicyToIndex(anyString(), anyString()))
        .thenReturn(new HashMap<>());
    when(openSearchISMOperations.removePolicyFromIndex(anyString())).thenReturn(new HashMap<>());

    underTest.checkAndUpdateIndices();
    verify(openSearchISMOperations, times(1))
        .addPolicyToIndex("index1*", OPERATE_DELETE_ARCHIVED_INDICES);
    verify(openSearchISMOperations, times(1)).removePolicyFromIndex("index2*");
    verify(openSearchISMOperations, times(1)).removePolicyFromIndex("index3*");
    verify(openSearchISMOperations, times(1))
        .addPolicyToIndex("template1*", OPERATE_DELETE_ARCHIVED_INDICES);
    verify(openSearchISMOperations, times(1)).removePolicyFromIndex("template2*");
  }
}
