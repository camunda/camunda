/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceAssert;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public class TenantAwareResourceFetchTest {

  private static final String TEST_RESOURCE = "/resource/test-rpa-1-with-version-tag-v1.rpa";

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  private CommandResponseWriter mockCommandResponseWriter;
  private ResourceRecord resourceResponse;

  @Before
  public void setUp() {
    mockCommandResponseWriter = engine.getCommandResponseWriter();
    interceptResponseWriter();
  }

  @Test
  public void shouldFetchResourceForAuthorizedTenant() throws Exception {
    // given
    final var resourceBytes = readFile(TEST_RESOURCE);
    final var deployment =
        engine
            .deployment()
            .withJsonResource(resourceBytes, "test.rpa")
            .withTenantId(TENANT_B)
            .deploy();
    final var resourceMetadata = deployment.getValue().getResourceMetadata().getFirst();
    final var resourceKey = resourceMetadata.getResourceKey();

    // when
    final var record =
        engine
            .resourceFetch()
            .withResourceKey(resourceKey)
            .withAuthorizedTenantIds(TENANT_A, TENANT_B)
            .fetch();

    // then
    ResourceAssert.assertThat(record.getValue())
        .isNotNull()
        .hasResourceProp(new String(resourceBytes))
        .hasResourceKey(resourceKey)
        .hasResourceId("Rpa_0w7r08e")
        .hasResourceName("test.rpa")
        .hasVersion(1)
        .hasVersionTag("v1.0")
        .hasDeploymentKey(deployment.getKey())
        .hasChecksum(resourceMetadata.getChecksum())
        .hasTenantId(TENANT_B);
    verify(mockCommandResponseWriter).intent(ResourceIntent.FETCHED);
    verify(mockCommandResponseWriter).valueType(ValueType.RESOURCE);
    verify(mockCommandResponseWriter).key(resourceKey);
    ResourceAssert.assertThat(resourceResponse)
        .isNotNull()
        .hasResourceProp(new String(resourceBytes))
        .hasResourceKey(resourceKey)
        .hasResourceId("Rpa_0w7r08e")
        .hasResourceName("test.rpa")
        .hasVersion(1)
        .hasVersionTag("v1.0")
        .hasDeploymentKey(deployment.getKey())
        .hasChecksum(resourceMetadata.getChecksum())
        .hasTenantId(TENANT_B);
  }

  @Test
  public void shouldNotFetchResourceForUnauthorizedTenant() throws Exception {
    // given
    final var resourceBytes = readFile(TEST_RESOURCE);
    final var deployment =
        engine
            .deployment()
            .withJsonResource(resourceBytes, "test.rpa")
            .withTenantId(TENANT_B)
            .deploy();
    final var resourceKey = deployment.getValue().getResourceMetadata().getFirst().getResourceKey();

    // when
    final var rejection =
        engine
            .resourceFetch()
            .withResourceKey(resourceKey)
            .withAuthorizedTenantIds(TENANT_A)
            .expectRejection()
            .fetch();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to fetch resource but no resource found with key `%d`"
                .formatted(resourceKey));
  }

  private static byte[] readFile(final String name) throws Exception {
    final var path = Path.of(TenantAwareResourceFetchTest.class.getResource(name).toURI());
    return Files.readAllBytes(path);
  }

  private void interceptResponseWriter() {
    doAnswer(
            (Answer<CommandResponseWriter>)
                (invocation -> {
                  final var arguments = invocation.getArguments();
                  if (arguments != null
                      && arguments.length == 1
                      && arguments[0] instanceof final ResourceRecord resourceRecord) {
                    resourceResponse = resourceRecord;
                  }
                  return mockCommandResponseWriter;
                }))
        .when(mockCommandResponseWriter)
        .valueWriter(any());
  }
}
