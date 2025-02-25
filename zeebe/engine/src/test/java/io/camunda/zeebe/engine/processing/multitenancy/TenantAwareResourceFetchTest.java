/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.AuthorizationUtil;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceAssert;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationWithTimeout;

public class TenantAwareResourceFetchTest {

  private static final String TEST_RESOURCE = "/resource/test-rpa-1-with-version-tag-v1.rpa";
  private static final VerificationWithTimeout VERIFICATION_TIMEOUT =
      timeout(Duration.ofSeconds(1).toMillis());

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
    verify(mockCommandResponseWriter, VERIFICATION_TIMEOUT).intent(ResourceIntent.FETCHED);
    verify(mockCommandResponseWriter, VERIFICATION_TIMEOUT).valueType(ValueType.RESOURCE);
    verify(mockCommandResponseWriter, VERIFICATION_TIMEOUT).key(resourceKey);
    assertThat(record.getValue())
        .satisfies(
            resourceProperties(
                resourceBytes,
                resourceKey,
                deployment.getKey(),
                resourceMetadata.getChecksum(),
                TENANT_B));
    assertThat(resourceResponse)
        .satisfies(
            resourceProperties(
                resourceBytes,
                resourceKey,
                deployment.getKey(),
                resourceMetadata.getChecksum(),
                TENANT_B));
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

  @Test
  public void shouldFetchResourceAssignedToCustomTenantWithAnonymousUser() throws Exception {
    // given
    engine.tenant().newTenant().withTenantId(TENANT_A).create();
    engine.tenant().newTenant().withTenantId(TENANT_B).create();
    final var resourceBytes = readFile(TEST_RESOURCE);
    final var deployment =
        engine
            .deployment()
            .withJsonResource(resourceBytes, "test.rpa")
            .withTenantId(TENANT_A)
            .deploy();
    final var resourceMetadata = deployment.getValue().getResourceMetadata().getFirst();
    final var resourceKey = resourceMetadata.getResourceKey();
    final var anonymous =
        AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true);

    // when
    final var record = engine.resourceFetch().withResourceKey(resourceKey).fetch(anonymous);

    // then
    verify(mockCommandResponseWriter, VERIFICATION_TIMEOUT).intent(ResourceIntent.FETCHED);
    verify(mockCommandResponseWriter, VERIFICATION_TIMEOUT).valueType(ValueType.RESOURCE);
    verify(mockCommandResponseWriter, VERIFICATION_TIMEOUT).key(resourceKey);
    assertThat(record.getValue())
        .satisfies(
            resourceProperties(
                resourceBytes,
                resourceKey,
                deployment.getKey(),
                resourceMetadata.getChecksum(),
                TENANT_A));
    assertThat(resourceResponse)
        .satisfies(
            resourceProperties(
                resourceBytes,
                resourceKey,
                deployment.getKey(),
                resourceMetadata.getChecksum(),
                TENANT_A));
  }

  @Test
  public void shouldFetchResourceAssignedToDefaultTenantWithAnonymousUser() throws Exception {
    // given
    final var resourceBytes = readFile(TEST_RESOURCE);
    final var deployment =
        engine
            .deployment()
            .withJsonResource(resourceBytes, "test.rpa")
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .deploy();
    final var resourceMetadata = deployment.getValue().getResourceMetadata().getFirst();
    final var resourceKey = resourceMetadata.getResourceKey();
    final var anonymous =
        AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true);

    // when
    final var record = engine.resourceFetch().withResourceKey(resourceKey).fetch(anonymous);

    // then
    verify(mockCommandResponseWriter, VERIFICATION_TIMEOUT).intent(ResourceIntent.FETCHED);
    verify(mockCommandResponseWriter, VERIFICATION_TIMEOUT).valueType(ValueType.RESOURCE);
    verify(mockCommandResponseWriter, VERIFICATION_TIMEOUT).key(resourceKey);
    assertThat(record.getValue())
        .satisfies(
            resourceProperties(
                resourceBytes,
                resourceKey,
                deployment.getKey(),
                resourceMetadata.getChecksum(),
                TenantOwned.DEFAULT_TENANT_IDENTIFIER));
    assertThat(resourceResponse)
        .satisfies(
            resourceProperties(
                resourceBytes,
                resourceKey,
                deployment.getKey(),
                resourceMetadata.getChecksum(),
                TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  public void shouldNotFetchResourceIfResourceNotFoundWithAnonymousUser() {
    // given
    final var unknownResourceKey = 123456789L;
    final var anonymous =
        AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true);

    // when
    final var rejection =
        engine
            .resourceFetch()
            .withResourceKey(unknownResourceKey)
            .expectRejection()
            .fetch(anonymous);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to fetch resource but no resource found with key `%d`"
                .formatted(unknownResourceKey));
  }

  private static byte[] readFile(final String name) throws Exception {
    final var path = Path.of(TenantAwareResourceFetchTest.class.getResource(name).toURI());
    return Files.readAllBytes(path);
  }

  private static Consumer<Resource> resourceProperties(
      final byte[] expectedResource,
      final long expectedResourceKey,
      final long expectedDeploymentKey,
      final byte[] expectedChecksum,
      final String expectedTenantId) {
    return resource ->
        ResourceAssert.assertThat(resource)
            .isNotNull()
            .hasResourceProp(new String(expectedResource))
            .hasResourceKey(expectedResourceKey)
            .hasResourceId("Rpa_0w7r08e")
            .hasResourceName("test.rpa")
            .hasVersion(1)
            .hasVersionTag("v1.0")
            .hasDeploymentKey(expectedDeploymentKey)
            .hasChecksum(expectedChecksum)
            .hasTenantId(expectedTenantId);
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
