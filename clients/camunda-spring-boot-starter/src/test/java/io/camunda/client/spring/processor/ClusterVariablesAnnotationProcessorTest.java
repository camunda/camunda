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
package io.camunda.client.spring.processor;

import static io.camunda.client.spring.testsupport.BeanInfoUtil.beanInfo;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.ClusterVariables;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.GloballyScopedClusterVariableCreationCommandStep1;
import io.camunda.client.api.command.GloballyScopedClusterVariableUpdateCommandStep1;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.command.TenantScopedClusterVariableCreationCommandStep1;
import io.camunda.client.api.command.TenantScopedClusterVariableUpdateCommandStep1;
import io.camunda.client.api.fetch.GloballyScopedClusterVariableGetRequest;
import io.camunda.client.api.fetch.TenantScopedClusterVariableGetRequest;
import io.camunda.client.api.search.response.ClusterVariable;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.spring.annotation.processor.ClusterVariablesAnnotationProcessor;
import io.camunda.client.spring.properties.CamundaClientClusterVariablesProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@ExtendWith(MockitoExtension.class)
public class ClusterVariablesAnnotationProcessorTest {

  @Mock private CamundaClient client;

  @Mock private GloballyScopedClusterVariableCreationCommandStep1 globalCreateStep;
  @Mock private GloballyScopedClusterVariableUpdateCommandStep1 globalUpdateStep;
  @Mock private GloballyScopedClusterVariableGetRequest globalGetStep;

  @Mock private TenantScopedClusterVariableCreationCommandStep1 tenantCreateStep;
  @Mock private TenantScopedClusterVariableUpdateCommandStep1 tenantUpdateStep;
  @Mock private TenantScopedClusterVariableGetRequest tenantGetStep;

  @Mock private ResourcePatternResolver resourcePatternResolver;

  @Spy private JsonMapper jsonMapper = new CamundaObjectMapper();

  @Spy
  private CamundaClientClusterVariablesProperties properties =
      new CamundaClientClusterVariablesProperties();

  @InjectMocks private ClusterVariablesAnnotationProcessor processor;

  @Test
  void shouldCreateSingleVariableFromResource() throws IOException {
    // given
    final Resource resource = mockJsonResource("{\"myVar\": \"myValue\"}");
    when(resourcePatternResolver.getResources("classpath:variables.json"))
        .thenReturn(new Resource[] {resource});
    mockGlobalCreateCommand();

    // when
    processor.configureFor(beanInfo(new WithSingleResource()));
    processor.start(client);

    // then
    verify(client).newGloballyScopedClusterVariableCreateRequest();
    verify(globalCreateStep).create("myVar", "myValue");
  }

  @Test
  void shouldCreateMultipleVariablesFromResource() throws IOException {
    // given
    final Resource resource =
        mockJsonResource("{\"var1\": \"value1\", \"var2\": 42, \"var3\": true}");
    when(resourcePatternResolver.getResources("classpath:variables.json"))
        .thenReturn(new Resource[] {resource});
    mockGlobalCreateCommand();

    // when
    processor.configureFor(beanInfo(new WithSingleResource()));
    processor.start(client);

    // then
    verify(globalCreateStep).create("var1", "value1");
    verify(globalCreateStep).create("var2", 42);
    verify(globalCreateStep).create("var3", true);
  }

  @Test
  void shouldCreateVariablesFromMethod() {
    // given
    mockGlobalCreateCommand();

    // when
    processor.configureFor(beanInfo(new WithMethodVariables()));
    processor.start(client);

    // then
    verify(globalCreateStep).create("environment", "production");
    verify(globalCreateStep).create("version", "1.0");
  }

  @Test
  void shouldCreateTenantScopedVariablesFromResource() throws IOException {
    // given
    final Resource resource = mockJsonResource("{\"tenantVar\": \"tenantValue\"}");
    when(resourcePatternResolver.getResources("classpath:tenant-variables.json"))
        .thenReturn(new Resource[] {resource});
    mockTenantCreateCommand();

    // when
    processor.configureFor(beanInfo(new WithTenantScopedResource()));
    processor.start(client);

    // then
    verify(client).newTenantScopedClusterVariableCreateRequest("my-tenant");
    verify(tenantCreateStep).create("tenantVar", "tenantValue");
  }

  @Test
  void shouldCreateTenantScopedVariablesFromMethod() {
    // given
    mockTenantCreateCommand();

    // when
    processor.configureFor(beanInfo(new WithTenantScopedMethod()));
    processor.start(client);

    // then
    verify(client).newTenantScopedClusterVariableCreateRequest("my-tenant");
    verify(tenantCreateStep).create("key", "value");
  }

  @Test
  void shouldNotCreateVariablesWhenNoneConfigured() {
    // when
    processor.start(client);

    // then
    verifyNoInteractions(client);
  }

  @Test
  void shouldClearValuesOnStop() throws IOException {
    // given
    mockGlobalCreateCommand();

    when(resourcePatternResolver.getResources("classpath:variables.json"))
        .thenAnswer(
            invocation -> {
              final Resource resource = mockJsonResource("{\"myVar\": \"myValue\"}");
              return new Resource[] {resource};
            });

    // when - first lifecycle round
    processor.configureFor(beanInfo(new WithSingleResource()));
    processor.start(client);

    // stop should clear values
    processor.stop(client);

    // configure and start again (second lifecycle round)
    processor.configureFor(beanInfo(new WithSingleResource()));
    processor.start(client);

    // then - create should have been called once per lifecycle round
    verify(globalCreateStep, times(2)).create("myVar", "myValue");
  }

  @Test
  void shouldThrowOnInvalidJsonResource() throws IOException {
    // given
    final Resource resource = mockJsonResource("not valid json");
    when(resourcePatternResolver.getResources("classpath:variables.json"))
        .thenReturn(new Resource[] {resource});

    // when / then
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(
            () -> {
              processor.configureFor(beanInfo(new WithSingleResource()));
              processor.start(client);
            });
  }

  @Test
  void shouldThrowWhenNoResourcesFound() throws IOException {
    // given
    when(resourcePatternResolver.getResources("classpath:variables.json"))
        .thenReturn(new Resource[0]);

    // when / then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              processor.configureFor(beanInfo(new WithSingleResource()));
              processor.start(client);
            })
        .withMessageContaining("No resources found");
  }

  @Test
  void shouldThrowWhenResourceResolutionFails() throws IOException {
    // given
    when(resourcePatternResolver.getResources("classpath:variables.json"))
        .thenThrow(new IOException("I/O error"));

    // when / then
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(
            () -> {
              processor.configureFor(beanInfo(new WithSingleResource()));
              processor.start(client);
            })
        .withMessageContaining("Error resolving cluster variables resources for pattern")
        .withCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldLoadVariablesFromMultipleResources() throws IOException {
    // given
    final Resource resource1 = mockJsonResource("{\"var1\": \"value1\"}");
    final Resource resource2 = mockJsonResource("{\"var2\": \"value2\"}");
    when(resourcePatternResolver.getResources("classpath:vars1.json"))
        .thenReturn(new Resource[] {resource1});
    when(resourcePatternResolver.getResources("classpath:vars2.json"))
        .thenReturn(new Resource[] {resource2});
    mockGlobalCreateCommand();

    // when
    processor.configureFor(beanInfo(new WithMultipleResources()));
    processor.start(client);

    // then
    verify(globalCreateStep).create("var1", "value1");
    verify(globalCreateStep).create("var2", "value2");
  }

  @Test
  void shouldCreateVariablesFromProperties() {
    // given
    properties.setVariables(Map.of("propVar1", "propValue1", "propVar2", 99));
    mockGlobalCreateCommand();

    // when
    processor.start(client);

    // then
    verify(globalCreateStep).create("propVar1", "propValue1");
    verify(globalCreateStep).create("propVar2", 99);
  }

  @Test
  void shouldCreateVariablesFromPojoMethod() {
    // given
    mockGlobalCreateCommand();

    // when
    processor.configureFor(beanInfo(new WithPojoMethod()));
    processor.start(client);

    // then
    verify(globalCreateStep).create("environment", "staging");
    verify(globalCreateStep).create("maxRetries", 5);
  }

  @Test
  void shouldCreateVariablesFromPropertiesAndAnnotations() throws IOException {
    // given
    properties.setVariables(Map.of("fromProp", "propValue"));
    final Resource resource = mockJsonResource("{\"fromResource\": \"resourceValue\"}");
    when(resourcePatternResolver.getResources("classpath:variables.json"))
        .thenReturn(new Resource[] {resource});
    mockGlobalCreateCommand();

    // when
    processor.configureFor(beanInfo(new WithSingleResource()));
    processor.start(client);

    // then
    verify(globalCreateStep).create("fromProp", "propValue");
    verify(globalCreateStep).create("fromResource", "resourceValue");
  }

  @Test
  void shouldUpdateGlobalVariableWhenAlreadyExists() throws IOException {
    // given
    final Resource resource = mockJsonResource("{\"myVar\": \"newValue\"}");
    when(resourcePatternResolver.getResources("classpath:variables.json"))
        .thenReturn(new Resource[] {resource});
    mockGlobalGetFound();
    mockGlobalUpdateCommand();

    // when
    processor.configureFor(beanInfo(new WithSingleResource()));
    processor.start(client);

    // then
    verify(globalUpdateStep).update("myVar", "newValue");
    verify(client, never()).newGloballyScopedClusterVariableCreateRequest();
  }

  @Test
  void shouldUpdateTenantScopedVariableWhenAlreadyExists() throws IOException {
    // given
    final Resource resource = mockJsonResource("{\"tenantVar\": \"newValue\"}");
    when(resourcePatternResolver.getResources("classpath:tenant-variables.json"))
        .thenReturn(new Resource[] {resource});
    mockTenantGetFound();
    mockTenantUpdateCommand();

    // when
    processor.configureFor(beanInfo(new WithTenantScopedResource()));
    processor.start(client);

    // then
    verify(tenantUpdateStep).update("tenantVar", "newValue");
    verify(client, never()).newTenantScopedClusterVariableCreateRequest(anyString());
  }

  private void mockGlobalCreateCommand() {
    mockGlobalGetNotFound();
    when(client.newGloballyScopedClusterVariableCreateRequest()).thenReturn(globalCreateStep);
    when(globalCreateStep.create(anyString(), any())).thenReturn(globalCreateStep);
  }

  private void mockGlobalGetNotFound() {
    when(client.newGloballyScopedClusterVariableGetRequest()).thenReturn(globalGetStep);
    when(globalGetStep.withName(anyString())).thenReturn(globalGetStep);
    doThrow(new ProblemException(404, "Not Found", null)).when(globalGetStep).execute();
  }

  private void mockGlobalGetFound() {
    when(client.newGloballyScopedClusterVariableGetRequest()).thenReturn(globalGetStep);
    when(globalGetStep.withName(anyString())).thenReturn(globalGetStep);
    when(globalGetStep.execute()).thenReturn(mock(ClusterVariable.class));
  }

  private void mockGlobalUpdateCommand() {
    when(client.newGloballyScopedClusterVariableUpdateRequest()).thenReturn(globalUpdateStep);
    when(globalUpdateStep.update(anyString(), any())).thenReturn(globalUpdateStep);
  }

  private void mockTenantCreateCommand() {
    mockTenantGetNotFound();
    when(client.newTenantScopedClusterVariableCreateRequest(anyString()))
        .thenReturn(tenantCreateStep);
    when(tenantCreateStep.create(anyString(), any())).thenReturn(tenantCreateStep);
  }

  private void mockTenantGetNotFound() {
    when(client.newTenantScopedClusterVariableGetRequest(anyString())).thenReturn(tenantGetStep);
    when(tenantGetStep.withName(anyString())).thenReturn(tenantGetStep);
    doThrow(new ProblemException(404, "Not Found", null)).when(tenantGetStep).execute();
  }

  private void mockTenantGetFound() {
    when(client.newTenantScopedClusterVariableGetRequest(anyString())).thenReturn(tenantGetStep);
    when(tenantGetStep.withName(anyString())).thenReturn(tenantGetStep);
    when(tenantGetStep.execute()).thenReturn(mock(ClusterVariable.class));
  }

  private void mockTenantUpdateCommand() {
    when(client.newTenantScopedClusterVariableUpdateRequest(anyString()))
        .thenReturn(tenantUpdateStep);
    when(tenantUpdateStep.update(anyString(), any())).thenReturn(tenantUpdateStep);
  }

  private Resource mockJsonResource(final String jsonContent) throws IOException {
    final Resource resource = mock(Resource.class);
    when(resource.getFilename()).thenReturn("test.json");
    when(resource.getInputStream())
        .thenReturn(new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8)));
    return resource;
  }

  // Public to allow reflective method invocation from SpringMethodInfo (different package)
  public static final class WithMethodVariables {
    @ClusterVariables
    public Map<String, Object> clusterVariables() {
      return Map.of("environment", "production", "version", "1.0");
    }
  }

  // Public to allow reflective method invocation from SpringMethodInfo (different package)
  public static final class WithTenantScopedMethod {
    @ClusterVariables(tenantId = "my-tenant")
    public Map<String, Object> tenantVariables() {
      return Map.of("key", "value");
    }
  }

  // Public to allow reflective method invocation from SpringMethodInfo (different package)
  public static final class WithPojoMethod {
    @ClusterVariables
    public MyVariables variables() {
      return new MyVariables("staging", 5);
    }
  }

  public static final class MyVariables {
    private final String environment;
    private final int maxRetries;

    public MyVariables(final String environment, final int maxRetries) {
      this.environment = environment;
      this.maxRetries = maxRetries;
    }

    public String getEnvironment() {
      return environment;
    }

    public int getMaxRetries() {
      return maxRetries;
    }
  }

  @ClusterVariables(resources = "classpath:variables.json")
  private static final class WithSingleResource {}

  @ClusterVariables(resources = {"classpath:vars1.json", "classpath:vars2.json"})
  private static final class WithMultipleResources {}

  @ClusterVariables(resources = "classpath:tenant-variables.json", tenantId = "my-tenant")
  private static final class WithTenantScopedResource {}
}
