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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.client.api.command.ActivateAdHocSubProcessActivitiesCommandStep1;
import io.camunda.client.api.command.ActivateAdHocSubProcessActivitiesCommandStep1.ActivateAdHocSubProcessActivitiesCommandStep2;
import io.camunda.client.protocol.rest.AdHocSubProcessActivateActivitiesInstruction;
import io.camunda.client.protocol.rest.AdHocSubProcessActivateActivityReference;
import io.camunda.client.util.ClientRestTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class AdHocSubProcessActivityActivationTest extends ClientRestTest {

  private static final String AD_HOC_SUBPROCESS_INSTANCE_KEY = "123456789";

  @ParameterizedTest
  @MethodSource("activateElementsWithoutVariablesModifiers")
  void shouldActivateAdHocSubProcessActivities(
      final Function<
              ActivateAdHocSubProcessActivitiesCommandStep1,
              ActivateAdHocSubProcessActivitiesCommandStep2>
          requestModifier) {
    final ActivateAdHocSubProcessActivitiesCommandStep1 command =
        client.newActivateAdHocSubProcessActivitiesCommand(AD_HOC_SUBPROCESS_INSTANCE_KEY);
    requestModifier.apply(command).send().join();

    final AdHocSubProcessActivateActivitiesInstruction request =
        gatewayService.getLastRequest(AdHocSubProcessActivateActivitiesInstruction.class);
    assertThat(request.getElements())
        .extracting(AdHocSubProcessActivateActivityReference::getElementId)
        .containsExactly("A", "B", "C");
  }

  @Test
  void shouldActivateAdHocSubProcessActivitiesCombiningActivationMethods() {
    client
        .newActivateAdHocSubProcessActivitiesCommand(AD_HOC_SUBPROCESS_INSTANCE_KEY)
        .activateElement("A")
        .activateElements("B", "C")
        .activateElements(Arrays.asList("D", "E"))
        .send()
        .join();

    final AdHocSubProcessActivateActivitiesInstruction request =
        gatewayService.getLastRequest(AdHocSubProcessActivateActivitiesInstruction.class);
    assertThat(request.getElements())
        .extracting(AdHocSubProcessActivateActivityReference::getElementId)
        .containsExactly("A", "B", "C", "D", "E");
  }

  @Test
  void shouldActivateAdHocSubProcessActivitiesWithVariables() {
    client
        .newActivateAdHocSubProcessActivitiesCommand(AD_HOC_SUBPROCESS_INSTANCE_KEY)
        .activateElement("A", mapOf(entry("A", "aValue")))
        .activateElement("B")
        .variables(mapOf(entry("B", "bValue")))
        .activateElement("C")
        .variables("{\"C\": \"cValue\", \"C2\": \"cValue2\"}")
        .activateElements(Collections.singletonList("D"))
        .activateElement("E")
        .activateElement("F")
        .variables(new ByteArrayInputStream("{\"F\": \"fValue\"}".getBytes(StandardCharsets.UTF_8)))
        .activateElement("G")
        .variable("G", "gValue")
        .send()
        .join();

    final AdHocSubProcessActivateActivitiesInstruction request =
        gatewayService.getLastRequest(AdHocSubProcessActivateActivitiesInstruction.class);
    assertThat(request.getElements())
        .extracting(
            AdHocSubProcessActivateActivityReference::getElementId,
            AdHocSubProcessActivateActivityReference::getVariables)
        .containsExactly(
            tuple("A", Collections.singletonMap("A", "aValue")),
            tuple("B", Collections.singletonMap("B", "bValue")),
            tuple("C", mapOf(Arrays.asList(entry("C", "cValue"), entry("C2", "cValue2")))),
            tuple("D", Collections.emptyMap()),
            tuple("E", Collections.emptyMap()),
            tuple("F", Collections.singletonMap("F", "fValue")),
            tuple("G", Collections.singletonMap("G", "gValue")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSetCancelRemainingInstances(final boolean cancelRemainingInstances) {
    client
        .newActivateAdHocSubProcessActivitiesCommand(AD_HOC_SUBPROCESS_INSTANCE_KEY)
        .activateElement("A")
        .cancelRemainingInstances(cancelRemainingInstances)
        .send()
        .join();

    final AdHocSubProcessActivateActivitiesInstruction request =
        gatewayService.getLastRequest(AdHocSubProcessActivateActivitiesInstruction.class);
    assertThat(request.getCancelRemainingInstances()).isEqualTo(cancelRemainingInstances);
  }

  @Test
  void shouldDefaultCancelRemainingInstancesToFalse() {
    client
        .newActivateAdHocSubProcessActivitiesCommand(AD_HOC_SUBPROCESS_INSTANCE_KEY)
        .activateElement("A")
        .send()
        .join();

    final AdHocSubProcessActivateActivitiesInstruction request =
        gatewayService.getLastRequest(AdHocSubProcessActivateActivitiesInstruction.class);
    assertThat(request.getCancelRemainingInstances()).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void throwsExceptionWhenElementsCollectionIsNullOrEmpty(final Collection<String> elementIds) {
    final ActivateAdHocSubProcessActivitiesCommandStep1 command =
        client.newActivateAdHocSubProcessActivitiesCommand(AD_HOC_SUBPROCESS_INSTANCE_KEY);

    assertThatThrownBy(() -> command.activateElements(elementIds))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("elementIds must not be empty");
  }

  @Test
  void throwsExceptionWhenElementsArrayIsEmpty() {
    final ActivateAdHocSubProcessActivitiesCommandStep1 command =
        client.newActivateAdHocSubProcessActivitiesCommand(AD_HOC_SUBPROCESS_INSTANCE_KEY);

    assertThatThrownBy(() -> command.activateElements(new String[] {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("elementIds must not be empty");
  }

  static Stream<
          Function<
              ActivateAdHocSubProcessActivitiesCommandStep1,
              ActivateAdHocSubProcessActivitiesCommandStep2>>
      activateElementsWithoutVariablesModifiers() {
    return Stream.of(
        command -> command.activateElement("A").activateElement("B").activateElement("C"),
        command -> command.activateElements("A", "B", "C"),
        command -> command.activateElements(Arrays.asList("A", "B", "C")));
  }

  private static Map<String, Object> mapOf(final Entry<String, Object> entry) {
    return mapOf(Collections.singletonList(entry));
  }

  private static Map<String, Object> mapOf(final List<Entry<String, Object>> entries) {
    final Map<String, Object> map = new HashMap<>();
    for (final Entry<String, Object> entry : entries) {
      map.put(entry.getKey(), entry.getValue());
    }
    return map;
  }
}
