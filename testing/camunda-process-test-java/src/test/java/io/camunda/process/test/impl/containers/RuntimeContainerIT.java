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
package io.camunda.process.test.impl.containers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.PartitionBrokerHealth;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.net.URI;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Only for demo purposes. Will be transformed into an integration test.")
public class RuntimeContainerIT {

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .userTask("A")
          .zeebeUserTask()
          .endEvent()
          .done();

  private static CamundaContainerRuntime runtime;

  @BeforeAll
  static void startContainerRuntime() {
    runtime = CamundaContainerRuntime.newBuilder().build();
    runtime.start();
  }

  @AfterAll
  static void closeContainerRuntime() throws Exception {
    runtime.close();
  }

  @Test
  void shouldConnectWithZeebeClient() {
    // given
    final ZeebeContainer zeebeContainer = runtime.getZeebeContainer();

    // when
    final ZeebeClient zeebeClient =
        ZeebeClient.newClientBuilder()
            .usePlaintext()
            .grpcAddress(zeebeContainer.getGrpcApiAddress())
            .restAddress(zeebeContainer.getRestApiAddress())
            .build();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              final Topology topology = zeebeClient.newTopologyRequest().send().join();

              assertThat(topology.getClusterSize()).isEqualTo(1);
              assertThat(topology.getPartitionsCount()).isEqualTo(1);

              assertThat(topology.getBrokers())
                  .flatExtracting(BrokerInfo::getPartitions)
                  .extracting(PartitionInfo::getHealth)
                  .containsOnly(PartitionBrokerHealth.HEALTHY);
            });
  }

  @Test
  void shouldExportToElasticsearch() {
    // given
    final ZeebeClient zeebeClient = createZeebeClient(runtime);
    zeebeClient.newDeployResourceCommand().addProcessModel(PROCESS, "process.bpmn").send().join();

    // when
    final String elasticsearchAddress = runtime.getElasticsearchContainer().getHttpHostAddress();
    final URI elasticsearchIndexStatsEndpoint =
        URI.create("http://" + elasticsearchAddress + "/_stats");

    final CloseableHttpClient httpClient = HttpClients.createDefault();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              final String responseBody =
                  sendGetRequest(httpClient, elasticsearchIndexStatsEndpoint);
              assertThat(responseBody).contains("zeebe-record_deployment");
            });
  }

  @Test
  void shouldFindProcessWithOperateApi() throws IOException {
    // given
    final ZeebeClient zeebeClient = createZeebeClient(runtime);
    final DeploymentEvent deployment =
        zeebeClient
            .newDeployResourceCommand()
            .addProcessModel(PROCESS, "process.bpmn")
            .send()
            .join();
    final long processDefinitionKey = deployment.getProcesses().get(0).getProcessDefinitionKey();

    // when
    final OperateContainer operateContainer = runtime.getOperateContainer();
    final String operateRestApi =
        "http://" + operateContainer.getHost() + ":" + operateContainer.getRestApiPort();
    final URI operateProcessEndpoint =
        URI.create(operateRestApi + "/v1/process-definitions/search");

    final BasicCookieStore cookieStore = new BasicCookieStore();
    final CloseableHttpClient httpClient =
        HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();

    sendLoginRequest(httpClient, operateRestApi);
    assertThat(cookieStore.getCookies()).extracting(Cookie::getName).contains("OPERATE-SESSION");

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              final String responseBody = sendPostRequest(httpClient, operateProcessEndpoint, "{}");
              assertThat(responseBody).contains("\"key\":" + processDefinitionKey);
            });
  }

  @Test
  void shouldFindUserTaskWithTasklistApi() throws IOException {
    // given
    final ZeebeClient zeebeClient = createZeebeClient(runtime);
    zeebeClient.newDeployResourceCommand().addProcessModel(PROCESS, "process.bpmn").send().join();

    final long processInstanceKey =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    // when
    final TasklistContainer tasklistContainer = runtime.getTasklistContainer();
    final String tasklistRestApi =
        "http://" + tasklistContainer.getHost() + ":" + tasklistContainer.getRestApiPort();
    final URI tasklistSearchTasksEndpoint = URI.create(tasklistRestApi + "/v1/tasks/search");
    final String searchTasksFilter = "{\n  \"processInstanceKey\": \"" + processInstanceKey + "\"}";

    final BasicCookieStore cookieStore = new BasicCookieStore();
    final CloseableHttpClient httpClient =
        HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();

    sendLoginRequest(httpClient, tasklistRestApi);
    assertThat(cookieStore.getCookies()).extracting(Cookie::getName).contains("TASKLIST-SESSION");

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              final String responseBody =
                  sendPostRequest(httpClient, tasklistSearchTasksEndpoint, searchTasksFilter);
              assertThat(responseBody)
                  .contains("\"processInstanceKey\" : \"" + processInstanceKey + "\"");
            });
  }

  private static ZeebeClient createZeebeClient(final CamundaContainerRuntime runtime) {
    final ZeebeContainer zeebeContainer = runtime.getZeebeContainer();
    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .grpcAddress(zeebeContainer.getGrpcApiAddress())
        .restAddress(zeebeContainer.getRestApiAddress())
        .build();
  }

  private static void sendLoginRequest(final CloseableHttpClient httpClient, final String apiUrl)
      throws IOException {
    httpClient.execute(
        new HttpPost(apiUrl + "/api/login?username=demo&password=demo"),
        response -> {
          assertThat(response.getCode()).isEqualTo(204);
          return null;
        });
  }

  private static String sendGetRequest(final CloseableHttpClient httpClient, final URI uri)
      throws IOException {
    return httpClient.execute(
        new HttpGet(uri),
        response -> {
          assertThat(response.getCode()).isEqualTo(200);
          return EntityUtils.toString(response.getEntity());
        });
  }

  private static String sendPostRequest(
      final CloseableHttpClient httpClient, final URI uri, final String body) throws IOException {

    final HttpPost request = new HttpPost(uri);
    request.setEntity(HttpEntities.create(body, ContentType.APPLICATION_JSON));

    return httpClient.execute(
        request,
        response -> {
          assertThat(response.getCode()).isEqualTo(200);
          return EntityUtils.toString(response.getEntity());
        });
  }
}
