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

import io.camunda.client.api.search.enums.AdHocSubProcessActivityResultType;
import io.camunda.client.api.search.response.AdHocSubProcessActivityResponse;
import io.camunda.client.api.search.response.AdHocSubProcessActivityResponse.AdHocSubProcessActivity;
import io.camunda.client.protocol.rest.AdHocSubProcessActivityResult;
import io.camunda.client.protocol.rest.AdHocSubProcessActivityResult.TypeEnum;
import io.camunda.client.protocol.rest.AdHocSubProcessActivitySearchQuery;
import io.camunda.client.protocol.rest.AdHocSubProcessActivitySearchQueryResult;
import io.camunda.client.util.ClientRestTest;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class AdHocSubProcessActivitySearchTest extends ClientRestTest {

  private static final Long PROCESS_DEFINITION_KEY = 2251799813685281L;
  private static final String PROCESS_DEFINITION_ID = "TestParentAdHocSubProcess";
  private static final String AD_HOC_SUBPROCESS_ID = "TestAdHocSubProcess";

  @Test
  void shouldSearchAdHocSubProcessActivities() {
    // given
    final AdHocSubProcessActivitySearchQueryResult searchQueryResult =
        new AdHocSubProcessActivitySearchQueryResult();
    searchQueryResult.addItemsItem(
        adHocSubProcessActivityResult(
            r -> {
              r.processDefinitionKey(String.valueOf(PROCESS_DEFINITION_KEY));
              r.processDefinitionId(PROCESS_DEFINITION_ID);
              r.setAdHocSubProcessId(AD_HOC_SUBPROCESS_ID);
              r.setElementId("task1");
              r.setElementName("Task #1");
              r.setType(TypeEnum.SERVICE_TASK);
              r.setDocumentation("The first task in the ad-hoc sub-process");
              r.setTenantId("<default>");
            }));
    searchQueryResult.addItemsItem(
        adHocSubProcessActivityResult(
            r -> {
              r.processDefinitionKey(String.valueOf(PROCESS_DEFINITION_KEY));
              r.processDefinitionId(PROCESS_DEFINITION_ID);
              r.setAdHocSubProcessId(AD_HOC_SUBPROCESS_ID);
              r.setElementId("task2");
              r.setElementName("Task #2");
              r.setType(TypeEnum.USER_TASK);
              r.setDocumentation("The second task in the ad-hoc sub-process");
              r.setTenantId("<default>");
            }));

    gatewayService.onAdHocSubProcessActivitySearch(searchQueryResult);

    // when
    final AdHocSubProcessActivityResponse response =
        client
            .newAdHocSubProcessActivitySearchRequest(PROCESS_DEFINITION_KEY, AD_HOC_SUBPROCESS_ID)
            .send()
            .join();

    // then
    final AdHocSubProcessActivitySearchQuery request =
        gatewayService.getLastRequest(AdHocSubProcessActivitySearchQuery.class);
    assertThat(request.getFilter().getProcessDefinitionKey())
        .isEqualTo(PROCESS_DEFINITION_KEY.toString());
    assertThat(request.getFilter().getAdHocSubProcessId()).isEqualTo(AD_HOC_SUBPROCESS_ID);

    assertThat(response.getItems())
        .hasSize(2)
        .extracting(
            AdHocSubProcessActivity::getProcessDefinitionKey,
            AdHocSubProcessActivity::getProcessDefinitionId,
            AdHocSubProcessActivity::getAdHocSubProcessId,
            AdHocSubProcessActivity::getElementId,
            AdHocSubProcessActivity::getElementName,
            AdHocSubProcessActivity::getType,
            AdHocSubProcessActivity::getDocumentation,
            AdHocSubProcessActivity::getTenantId)
        .containsExactly(
            tuple(
                PROCESS_DEFINITION_KEY,
                PROCESS_DEFINITION_ID,
                AD_HOC_SUBPROCESS_ID,
                "task1",
                "Task #1",
                AdHocSubProcessActivityResultType.SERVICE_TASK,
                "The first task in the ad-hoc sub-process",
                "<default>"),
            tuple(
                PROCESS_DEFINITION_KEY,
                PROCESS_DEFINITION_ID,
                AD_HOC_SUBPROCESS_ID,
                "task2",
                "Task #2",
                AdHocSubProcessActivityResultType.USER_TASK,
                "The second task in the ad-hoc sub-process",
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
            + "      \"processDefinitionId\": \"TestParentAdHocSubProcess\",\n"
            + "      \"adHocSubProcessId\": \"TestAdHocSubProcess\",\n"
            + "      \"elementId\": \"unknownTask\",\n"
            + "      \"elementName\": \"Unknown Task\",\n"
            + "      \"type\": \"START_EVENT\",\n"
            + "      \"tenantId\": \"<default>\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    gatewayService.onAdHocSubProcessActivitySearch(responseJson);

    // when
    final AdHocSubProcessActivityResponse response =
        client
            .newAdHocSubProcessActivitySearchRequest(PROCESS_DEFINITION_KEY, AD_HOC_SUBPROCESS_ID)
            .send()
            .join();

    // then
    assertThat(response.getItems())
        .hasSize(1)
        .extracting(
            AdHocSubProcessActivity::getProcessDefinitionKey,
            AdHocSubProcessActivity::getProcessDefinitionId,
            AdHocSubProcessActivity::getAdHocSubProcessId,
            AdHocSubProcessActivity::getElementId,
            AdHocSubProcessActivity::getElementName,
            AdHocSubProcessActivity::getType,
            AdHocSubProcessActivity::getDocumentation,
            AdHocSubProcessActivity::getTenantId)
        .containsExactly(
            tuple(
                PROCESS_DEFINITION_KEY,
                PROCESS_DEFINITION_ID,
                AD_HOC_SUBPROCESS_ID,
                "unknownTask",
                "Unknown Task",
                AdHocSubProcessActivityResultType.UNKNOWN_ENUM_VALUE,
                null,
                "<default>"));
  }

  private static AdHocSubProcessActivityResult adHocSubProcessActivityResult(
      final Consumer<AdHocSubProcessActivityResult> consumer) {
    final AdHocSubProcessActivityResult result = new AdHocSubProcessActivityResult();
    consumer.accept(result);
    return result;
  }
}
