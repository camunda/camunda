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
package io.camunda.client.adhocsubprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.api.search.response.AdHocSubprocessActivityResponse;
import io.camunda.client.api.search.response.AdHocSubprocessActivityResponse.AdHocSubprocessActivity;
import io.camunda.client.protocol.rest.AdHocSubprocessActivityResult;
import io.camunda.client.protocol.rest.AdHocSubprocessActivityResult.TypeEnum;
import io.camunda.client.protocol.rest.AdHocSubprocessActivitySearchQuery;
import io.camunda.client.protocol.rest.AdHocSubprocessActivitySearchQueryResult;
import io.camunda.client.util.ClientRestTest;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class AdHocSubprocessActivitySearchTest extends ClientRestTest {

  private static final Long PROCESS_DEFINITION_KEY = 2251799813685281L;
  private static final String PROCESS_DEFINITION_ID = "TestParentAdHocSubprocess";
  private static final String AD_HOC_SUBPROCESS_ID = "TestAdHocSubprocess";

  @Test
  void shouldSearchAdHocSubprocessActivities() {
    // given
    final AdHocSubprocessActivitySearchQueryResult searchQueryResult =
        new AdHocSubprocessActivitySearchQueryResult();
    searchQueryResult.addItemsItem(
        adHocSubprocessActivityResult(
            r -> {
              r.processDefinitionKey(String.valueOf(PROCESS_DEFINITION_KEY));
              r.processDefinitionId(PROCESS_DEFINITION_ID);
              r.setAdHocSubprocessId(AD_HOC_SUBPROCESS_ID);
              r.setElementId("task1");
              r.setElementName("Task #1");
              r.setType(TypeEnum.SERVICE_TASK);
              r.setDocumentation("The first task in the ad-hoc subprocess");
              r.setTenantId("<default>");
            }));
    searchQueryResult.addItemsItem(
        adHocSubprocessActivityResult(
            r -> {
              r.processDefinitionKey(String.valueOf(PROCESS_DEFINITION_KEY));
              r.processDefinitionId(PROCESS_DEFINITION_ID);
              r.setAdHocSubprocessId(AD_HOC_SUBPROCESS_ID);
              r.setElementId("task2");
              r.setElementName("Task #2");
              r.setType(TypeEnum.USER_TASK);
              r.setDocumentation("The second task in the ad-hoc subprocess");
              r.setTenantId("<default>");
            }));

    gatewayService.onAdHocSubprocessActivitySearch(searchQueryResult);

    // when
    final AdHocSubprocessActivityResponse response =
        client
            .newAdHocSubprocessActivityQuery(PROCESS_DEFINITION_KEY, AD_HOC_SUBPROCESS_ID)
            .send()
            .join();

    // then
    final AdHocSubprocessActivitySearchQuery request =
        gatewayService.getLastRequest(AdHocSubprocessActivitySearchQuery.class);
    assertThat(request.getFilter().getProcessDefinitionKey())
        .isEqualTo(PROCESS_DEFINITION_KEY.toString());
    assertThat(request.getFilter().getAdHocSubprocessId()).isEqualTo(AD_HOC_SUBPROCESS_ID);

    assertThat(response.getItems())
        .hasSize(2)
        .extracting(
            AdHocSubprocessActivity::getProcessDefinitionKey,
            AdHocSubprocessActivity::getProcessDefinitionId,
            AdHocSubprocessActivity::getAdHocSubprocessId,
            AdHocSubprocessActivity::getElementId,
            AdHocSubprocessActivity::getElementName,
            AdHocSubprocessActivity::getType,
            AdHocSubprocessActivity::getDocumentation,
            AdHocSubprocessActivity::getTenantId)
        .containsExactly(
            tuple(
                PROCESS_DEFINITION_KEY,
                PROCESS_DEFINITION_ID,
                AD_HOC_SUBPROCESS_ID,
                "task1",
                "Task #1",
                io.camunda.client.wrappers.AdHocSubprocessActivityResult.Type.SERVICE_TASK,
                "The first task in the ad-hoc subprocess",
                "<default>"),
            tuple(
                PROCESS_DEFINITION_KEY,
                PROCESS_DEFINITION_ID,
                AD_HOC_SUBPROCESS_ID,
                "task2",
                "Task #2",
                io.camunda.client.wrappers.AdHocSubprocessActivityResult.Type.USER_TASK,
                "The second task in the ad-hoc subprocess",
                "<default>"));
  }

  @Test
  void shouldMapUnknownEnumValueToFallbackValueWithoutBreaking() {
    // given
    final String responseJson =
        "{\n"
            + "  \"items\": [\n"
            + "    {\n"
            + "      \"processDefinitionKey\": \"2251799813685281\",\n"
            + "      \"processDefinitionId\": \"TestParentAdHocSubprocess\",\n"
            + "      \"adHocSubprocessId\": \"TestAdHocSubprocess\",\n"
            + "      \"elementId\": \"unknownTask\",\n"
            + "      \"elementName\": \"Unknown Task\",\n"
            + "      \"type\": \"START_EVENT\",\n"
            + "      \"tenantId\": \"<default>\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    gatewayService.onAdHocSubprocessActivitySearch(responseJson);

    // when
    final AdHocSubprocessActivityResponse response =
        client
            .newAdHocSubprocessActivityQuery(PROCESS_DEFINITION_KEY, AD_HOC_SUBPROCESS_ID)
            .send()
            .join();

    // then
    assertThat(response.getItems())
        .hasSize(1)
        .extracting(
            AdHocSubprocessActivity::getProcessDefinitionKey,
            AdHocSubprocessActivity::getProcessDefinitionId,
            AdHocSubprocessActivity::getAdHocSubprocessId,
            AdHocSubprocessActivity::getElementId,
            AdHocSubprocessActivity::getElementName,
            AdHocSubprocessActivity::getType,
            AdHocSubprocessActivity::getDocumentation,
            AdHocSubprocessActivity::getTenantId)
        .containsExactly(
            tuple(
                PROCESS_DEFINITION_KEY,
                PROCESS_DEFINITION_ID,
                AD_HOC_SUBPROCESS_ID,
                "unknownTask",
                "Unknown Task",
                io.camunda.client.wrappers.AdHocSubprocessActivityResult.Type.UNKNOWN_ENUM_VALUE,
                null,
                "<default>"));
  }

  private static AdHocSubprocessActivityResult adHocSubprocessActivityResult(
      final Consumer<AdHocSubprocessActivityResult> consumer) {
    final AdHocSubprocessActivityResult result = new AdHocSubprocessActivityResult();
    consumer.accept(result);
    return result;
  }
}
