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
package io.camunda.zeebe.protocol.record.intent;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class IntentEncodingDecodingTest {

  @ParameterizedTest
  @MethodSource("parameters")
  void shouldEncodeAndDecodeTimerIntent(final ParameterSet parameterSet) {
    final short value = parameterSet.intent.value();

    final Intent decoded = parameterSet.decoder.apply(value);

    assertThat(decoded).isSameAs(parameterSet.intent);
  }

  private static Stream<ParameterSet> parameters() {
    final List<ParameterSet> result = new ArrayList<>();
    result.addAll(
        buildParameterSets(DecisionEvaluationIntent.class, DecisionEvaluationIntent::from));
    result.addAll(buildParameterSets(DecisionIntent.class, DecisionIntent::from));
    result.addAll(
        buildParameterSets(DecisionRequirementsIntent.class, DecisionRequirementsIntent::from));
    result.addAll(
        buildParameterSets(DeploymentDistributionIntent.class, DeploymentDistributionIntent::from));
    result.addAll(buildParameterSets(DeploymentIntent.class, DeploymentIntent::from));
    result.addAll(buildParameterSets(ErrorIntent.class, ErrorIntent::from));
    result.addAll(buildParameterSets(IncidentIntent.class, IncidentIntent::from));
    result.addAll(buildParameterSets(JobBatchIntent.class, JobBatchIntent::from));
    result.addAll(buildParameterSets(JobIntent.class, JobIntent::from));
    result.addAll(buildParameterSets(MessageIntent.class, MessageIntent::from));
    result.addAll(
        buildParameterSets(
            MessageStartEventSubscriptionIntent.class, MessageStartEventSubscriptionIntent::from));
    result.addAll(
        buildParameterSets(MessageSubscriptionIntent.class, MessageSubscriptionIntent::from));
    result.addAll(buildParameterSets(ProcessEventIntent.class, ProcessEventIntent::from));
    result.addAll(
        buildParameterSets(
            ProcessInstanceCreationIntent.class, ProcessInstanceCreationIntent::from));
    result.addAll(buildParameterSets(ProcessInstanceIntent.class, ProcessInstanceIntent::from));
    result.addAll(
        buildParameterSets(ProcessInstanceResultIntent.class, ProcessInstanceResultIntent::from));
    result.addAll(buildParameterSets(ProcessIntent.class, ProcessIntent::from));
    result.addAll(
        buildParameterSets(
            ProcessMessageSubscriptionIntent.class, ProcessMessageSubscriptionIntent::from));
    result.addAll(buildParameterSets(TimerIntent.class, TimerIntent::from));
    result.addAll(buildParameterSets(VariableDocumentIntent.class, VariableDocumentIntent::from));
    result.addAll(buildParameterSets(VariableIntent.class, VariableIntent::from));
    result.addAll(buildParameterSets(CheckpointIntent.class, CheckpointIntent::from));
    result.addAll(buildParameterSets(EscalationIntent.class, EscalationIntent::from));
    result.addAll(buildParameterSets(SignalIntent.class, SignalIntent::from));
    result.addAll(
        buildParameterSets(SignalSubscriptionIntent.class, SignalSubscriptionIntent::from));
    result.addAll(buildParameterSets(ClockIntent.class, ClockIntent::from));
    result.addAll(buildParameterSets(TenantIntent.class, TenantIntent::from));

    return result.stream();
  }

  private static List<ParameterSet> buildParameterSets(
      final Class<? extends Enum<? extends Intent>> intentClass,
      final Function<Short, Intent> decoder) {
    final List<ParameterSet> result = new ArrayList<>();

    for (final Enum<? extends Intent> intent : intentClass.getEnumConstants()) {
      result.add(new ParameterSet((Intent) intent, decoder));
    }

    return result;
  }

  private static final class ParameterSet {
    final Intent intent;
    final Function<Short, Intent> decoder;

    private ParameterSet(final Intent intent, final Function<Short, Intent> decoder) {
      this.intent = intent;
      this.decoder = decoder;
    }

    @Override
    public String toString() {
      return "intent=" + intent.getClass().getSimpleName() + "." + intent.name();
    }
  }
}
