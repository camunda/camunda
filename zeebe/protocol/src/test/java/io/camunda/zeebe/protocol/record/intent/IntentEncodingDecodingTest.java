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
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class IntentEncodingDecodingTest {

  @ParameterizedTest
  @MethodSource("parameters")
  void shouldEncodeAndDecodeIntent(final ParameterSet parameterSet) {
    final short value = parameterSet.intent.value();

    final Intent decoded = parameterSet.decoder.apply(value);

    assertThat(decoded).isSameAs(parameterSet.intent);
  }

  @Test
  void shouldCoverAllIntentsInThisTest() {
    // when
    final Stream<ParameterSet> parameters = parameters();

    // then
    final Set<Class<? extends Intent>> actualIntentClasses =
        parameters.map(parameterSet -> parameterSet.intent.getClass()).collect(Collectors.toSet());
    assertThat(actualIntentClasses).containsAll(new ArrayList<>(Intent.INTENT_CLASSES));
  }

  private static Stream<ParameterSet> parameters() {
    final List<ParameterSet> result = new ArrayList<>();
    result.addAll(buildParameterSets(AgentHistoryIntent.class, AgentHistoryIntent::from));
    result.addAll(buildParameterSets(AgentInstanceIntent.class, AgentInstanceIntent::from));
    result.addAll(
        buildParameterSets(
            AdHocSubProcessInstructionIntent.class, AdHocSubProcessInstructionIntent::from));
    result.addAll(buildParameterSets(AsyncRequestIntent.class, AsyncRequestIntent::from));
    result.addAll(buildParameterSets(AuthorizationIntent.class, AuthorizationIntent::from));
    result.addAll(
        buildParameterSets(BatchOperationChunkIntent.class, BatchOperationChunkIntent::from));
    result.addAll(buildParameterSets(BatchOperationIntent.class, BatchOperationIntent::from));
    result.addAll(
        buildParameterSets(
            BatchOperationExecutionIntent.class, BatchOperationExecutionIntent::from));
    result.addAll(buildParameterSets(CheckpointIntent.class, CheckpointIntent::from));
    result.addAll(buildParameterSets(ClockIntent.class, ClockIntent::from));
    result.addAll(buildParameterSets(ClusterVariableIntent.class, ClusterVariableIntent::from));
    result.addAll(
        buildParameterSets(CommandDistributionIntent.class, CommandDistributionIntent::from));
    result.addAll(
        buildParameterSets(
            CompensationSubscriptionIntent.class, CompensationSubscriptionIntent::from));
    result.addAll(
        buildParameterSets(ConditionalEvaluationIntent.class, ConditionalEvaluationIntent::from));
    result.addAll(
        buildParameterSets(
            ConditionalSubscriptionIntent.class, ConditionalSubscriptionIntent::from));
    result.addAll(buildParameterSets(DecisionIntent.class, DecisionIntent::from));
    result.addAll(
        buildParameterSets(DecisionEvaluationIntent.class, DecisionEvaluationIntent::from));
    result.addAll(
        buildParameterSets(DecisionRequirementsIntent.class, DecisionRequirementsIntent::from));
    result.addAll(buildParameterSets(DeploymentIntent.class, DeploymentIntent::from));
    result.addAll(
        buildParameterSets(DeploymentDistributionIntent.class, DeploymentDistributionIntent::from));
    result.addAll(buildParameterSets(ErrorIntent.class, ErrorIntent::from));
    result.addAll(buildParameterSets(EscalationIntent.class, EscalationIntent::from));
    result.addAll(buildParameterSets(ExpressionIntent.class, ExpressionIntent::from));
    result.addAll(buildParameterSets(FormIntent.class, FormIntent::from));
    result.addAll(buildParameterSets(GlobalListenerIntent.class, GlobalListenerIntent::from));
    result.addAll(
        buildParameterSets(GlobalListenerBatchIntent.class, GlobalListenerBatchIntent::from));
    result.addAll(buildParameterSets(GroupIntent.class, GroupIntent::from));
    result.addAll(buildParameterSets(HistoryDeletionIntent.class, HistoryDeletionIntent::from));
    result.addAll(buildParameterSets(IdentitySetupIntent.class, IdentitySetupIntent::from));
    result.addAll(buildParameterSets(IncidentIntent.class, IncidentIntent::from));
    result.addAll(buildParameterSets(JobIntent.class, JobIntent::from));
    result.addAll(buildParameterSets(JobBatchIntent.class, JobBatchIntent::from));
    result.addAll(buildParameterSets(JobMetricsBatchIntent.class, JobMetricsBatchIntent::from));
    result.addAll(buildParameterSets(MappingRuleIntent.class, MappingRuleIntent::from));
    result.addAll(buildParameterSets(MessageIntent.class, MessageIntent::from));
    result.addAll(buildParameterSets(MessageBatchIntent.class, MessageBatchIntent::from));
    result.addAll(
        buildParameterSets(MessageCorrelationIntent.class, MessageCorrelationIntent::from));
    result.addAll(
        buildParameterSets(
            MessageStartEventSubscriptionIntent.class, MessageStartEventSubscriptionIntent::from));
    result.addAll(
        buildParameterSets(
            MessageStartCorrelationKeyLockReleaseIntent.class,
            MessageStartCorrelationKeyLockReleaseIntent::from));
    result.addAll(
        buildParameterSets(
            MessageStartProcessInstanceRequestIntent.class,
            MessageStartProcessInstanceRequestIntent::from));
    result.addAll(
        buildParameterSets(MessageSubscriptionIntent.class, MessageSubscriptionIntent::from));
    result.addAll(buildParameterSets(MultiInstanceIntent.class, MultiInstanceIntent::from));
    result.addAll(buildParameterSets(ProcessIntent.class, ProcessIntent::from));
    result.addAll(buildParameterSets(ProcessEventIntent.class, ProcessEventIntent::from));
    result.addAll(buildParameterSets(ProcessInstanceIntent.class, ProcessInstanceIntent::from));
    result.addAll(
        buildParameterSets(ProcessInstanceBatchIntent.class, ProcessInstanceBatchIntent::from));
    result.addAll(
        buildParameterSets(
            ProcessInstanceCreationIntent.class, ProcessInstanceCreationIntent::from));
    result.addAll(
        buildParameterSets(
            ProcessInstanceMigrationIntent.class, ProcessInstanceMigrationIntent::from));
    result.addAll(
        buildParameterSets(
            ProcessInstanceModificationIntent.class, ProcessInstanceModificationIntent::from));
    result.addAll(
        buildParameterSets(ProcessInstanceResultIntent.class, ProcessInstanceResultIntent::from));
    result.addAll(
        buildParameterSets(
            ProcessMessageSubscriptionIntent.class, ProcessMessageSubscriptionIntent::from));
    result.addAll(buildParameterSets(ResourceIntent.class, ResourceIntent::from));
    result.addAll(buildParameterSets(ResourceDeletionIntent.class, ResourceDeletionIntent::from));
    result.addAll(buildParameterSets(ResourceReexportIntent.class, ResourceReexportIntent::from));
    result.addAll(buildParameterSets(RoleIntent.class, RoleIntent::from));
    result.addAll(
        buildParameterSets(RuntimeInstructionIntent.class, RuntimeInstructionIntent::from));
    result.addAll(buildParameterSets(ScaleIntent.class, ScaleIntent::from));
    result.addAll(buildParameterSets(SignalIntent.class, SignalIntent::from));
    result.addAll(
        buildParameterSets(SignalSubscriptionIntent.class, SignalSubscriptionIntent::from));
    result.addAll(buildParameterSets(TenantIntent.class, TenantIntent::from));
    result.addAll(buildParameterSets(TimerIntent.class, TimerIntent::from));
    result.addAll(buildParameterSets(UsageMetricIntent.class, UsageMetricIntent::from));
    result.addAll(buildParameterSets(UserIntent.class, UserIntent::from));
    result.addAll(buildParameterSets(UserTaskIntent.class, UserTaskIntent::from));
    result.addAll(buildParameterSets(VariableDocumentIntent.class, VariableDocumentIntent::from));
    result.addAll(buildParameterSets(VariableIntent.class, VariableIntent::from));

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
