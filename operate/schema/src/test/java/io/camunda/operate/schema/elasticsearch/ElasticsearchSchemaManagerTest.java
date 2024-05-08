/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.elasticsearch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.property.ArchiverProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchSchemaManagerTest {

  @Mock protected RetryElasticsearchClient retryElasticsearchClient;
  @Mock protected OperateProperties operateProperties;
  @Mock private List<AbstractIndexDescriptor> indexDescriptors;
  @Mock private List<TemplateDescriptor> templateDescriptors;
  @Mock private List<Integer> testList;
  @InjectMocks private ElasticsearchSchemaManager underTest;

  @Test
  public void testCheckAndUpdateIndices() {
    final AbstractIndexDescriptor abstractIndexDescriptor1 = mock(AbstractIndexDescriptor.class);
    final AbstractIndexDescriptor abstractIndexDescriptor2 = mock(AbstractIndexDescriptor.class);
    final AbstractIndexDescriptor abstractIndexDescriptor3 = mock(AbstractIndexDescriptor.class);
    when(abstractIndexDescriptor1.getDerivedIndexNamePattern()).thenReturn("index1*");
    when(abstractIndexDescriptor2.getDerivedIndexNamePattern()).thenReturn("index2*");
    when(abstractIndexDescriptor3.getDerivedIndexNamePattern()).thenReturn("index3*");

    final TemplateDescriptor templateDescriptor1 = mock(TemplateDescriptor.class);
    when(templateDescriptor1.getDerivedIndexNamePattern()).thenReturn("template1*");
    final TemplateDescriptor templateDescriptor2 = mock(TemplateDescriptor.class);
    when(templateDescriptor2.getDerivedIndexNamePattern()).thenReturn("template2*");

    ReflectionTestUtils.setField(
        underTest,
        "indexDescriptors",
        Arrays.asList(
            abstractIndexDescriptor1, abstractIndexDescriptor2, abstractIndexDescriptor3));

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

    when(retryElasticsearchClient.setIndexSettingsFor(any(), anyString())).thenReturn(true);
    when(retryElasticsearchClient.deleteIndexPolicyFor(anyString())).thenReturn(true);

    underTest.checkAndUpdateIndices();

    verify(retryElasticsearchClient, times(2)).setIndexSettingsFor(any(), anyString());
    verify(retryElasticsearchClient, times(3)).deleteIndexPolicyFor(anyString());
  }
}
