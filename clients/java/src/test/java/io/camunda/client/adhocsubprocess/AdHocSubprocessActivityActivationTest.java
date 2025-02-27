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

import io.camunda.client.api.command.ActivateAdHocSubprocessActivitiesCommandStep1;
import io.camunda.client.api.command.ActivateAdHocSubprocessActivitiesCommandStep1.ActivateAdHocSubprocessActivitiesCommandStep2;
import io.camunda.client.protocol.rest.AdHocSubprocessActivateActivitiesInstruction;
import io.camunda.client.protocol.rest.AdHocSubprocessActivateActivityReference;
import io.camunda.client.util.ClientRestTest;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

public class AdHocSubprocessActivityActivationTest extends ClientRestTest {

  private static final String AD_HOC_SUBPROCESS_INSTANCE_KEY = "123456789";

  @ParameterizedTest
  @MethodSource("requestModifiers")
  void shouldActivateAdHocSubprocessActivities(
      final Function<
              ActivateAdHocSubprocessActivitiesCommandStep1,
              ActivateAdHocSubprocessActivitiesCommandStep2>
          requestModifier) {
    final ActivateAdHocSubprocessActivitiesCommandStep1 command =
        client.newActivateAdHocSubprocessActivitiesCommand(AD_HOC_SUBPROCESS_INSTANCE_KEY);
    requestModifier.apply(command).send().join();

    final AdHocSubprocessActivateActivitiesInstruction request =
        gatewayService.getLastRequest(AdHocSubprocessActivateActivitiesInstruction.class);
    assertThat(request.getElements())
        .extracting(AdHocSubprocessActivateActivityReference::getElementId)
        .containsExactly("A", "B", "C");
  }

  @Test
  void shouldActivateAdHocSubprocessActivitiesCombiningActivationMethods() {
    client
        .newActivateAdHocSubprocessActivitiesCommand(AD_HOC_SUBPROCESS_INSTANCE_KEY)
        .activateElement("A")
        .activateElements("B", "C")
        .activateElements(Arrays.asList("D", "E"))
        .send()
        .join();

    final AdHocSubprocessActivateActivitiesInstruction request =
        gatewayService.getLastRequest(AdHocSubprocessActivateActivitiesInstruction.class);
    assertThat(request.getElements())
        .extracting(AdHocSubprocessActivateActivityReference::getElementId)
        .containsExactly("A", "B", "C", "D", "E");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void throwsExceptionWhenElementsCollectionIsNullOrEmpty(final Collection<String> elementIds) {
    final ActivateAdHocSubprocessActivitiesCommandStep1 command =
        client.newActivateAdHocSubprocessActivitiesCommand(AD_HOC_SUBPROCESS_INSTANCE_KEY);

    assertThatThrownBy(() -> command.activateElements(elementIds))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("elementIds must not be empty");
  }

  @Test
  void throwsExceptionWhenElementsArrayIsEmpty() {
    final ActivateAdHocSubprocessActivitiesCommandStep1 command =
        client.newActivateAdHocSubprocessActivitiesCommand(AD_HOC_SUBPROCESS_INSTANCE_KEY);

    assertThatThrownBy(() -> command.activateElements(new String[] {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("elementIds must not be empty");
  }

  static Stream<
          Function<
              ActivateAdHocSubprocessActivitiesCommandStep1,
              ActivateAdHocSubprocessActivitiesCommandStep2>>
      requestModifiers() {
    return Stream.of(
        command -> command.activateElement("A").activateElement("B").activateElement("C"),
        command -> command.activateElements("A", "B", "C"),
        command -> command.activateElements(Arrays.asList("A", "B", "C")));
  }
}
