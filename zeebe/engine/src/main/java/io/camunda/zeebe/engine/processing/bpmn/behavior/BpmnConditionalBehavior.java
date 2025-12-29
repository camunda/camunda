/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.conditional.ConditionalSubscription;
import io.camunda.zeebe.engine.state.immutable.ConditionalSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BpmnConditionalBehavior {

  private final ElementInstanceState elementInstanceState;
  private final ConditionalSubscriptionState conditionSubscriptionState;
  private final TypedCommandWriter commandWriter;
  private final ExpressionProcessor expressionProcessor;
  private final ExpressionLanguage expressionLanguage;

  public BpmnConditionalBehavior(
      final ProcessingState processingState,
      final TypedCommandWriter commandWriter,
      final ExpressionProcessor expressionProcessor,
      final ExpressionLanguage expressionLanguage) {
    elementInstanceState = processingState.getElementInstanceState();
    conditionSubscriptionState = processingState.getConditionalSubscriptionState();
    this.commandWriter = commandWriter;
    this.expressionProcessor = expressionProcessor;
    this.expressionLanguage = expressionLanguage;
  }

  /**
   * Evaluates conditional subscriptions when variable events are present.
   *
   * <p>Key rules:
   *
   * <ol>
   *   <li>Each subscription must be triggered at most once per document merge.
   *   <li>For every scope in which a variable is created or updated, evaluate the conditional
   *       subscriptions for that scope.
   *   <li>Evaluation must proceed top-down. For each child scope, evaluate its conditionals as part
   *       of the traversal.
   * </ol>
   *
   * <p>Stop the traversal when either of the following occurs:
   *
   * <ul>
   *   <li>An interrupting subscription is triggered.
   *   <li>No more child scopes exist.
   * </ul>
   *
   * <p>Before evaluating a scope's conditional subscriptions, apply the {@code variableNamesFilter}
   * and {@code variableEventsFilter} filters. Only if there is a match, evaluate the condition.
   *
   * <p>Note: These filters are optional. If they are not set, apply the filter as passed.
   *
   * @param processDefinitionKey the process definition key to evaluate conditionals for
   * @param variableEvents the list of variable events to evaluate conditionals for
   */
  public void evaluateConditionals(
      final long processDefinitionKey, final List<VariableEvent> variableEvents) {

    // performance optimization: skip evaluation if no conditional subscriptions exist for the
    // process definition
    if (!isConditionalSubscriptionExist(processDefinitionKey)) {
      return;
    }

    // each subscription must be triggered only once per document merge
    final Set<Long> triggeredSubscriptionKeys = new HashSet<>();
    // optimize the traversal by storing interrupted scopes
    final Set<Long> interruptedScopes = new HashSet<>();
    // cache parsed expressions to avoid parsing the same expression multiple times
    final Map<Long, Expression> cachedExpressions = new HashMap<>();

    for (final VariableEvent variableEvent : variableEvents) {
      // start top-down traversal from the scope key where the variable was set
      final var scopes = new ArrayDeque<>(List.of(variableEvent.scopeKey));

      while (!scopes.isEmpty()) {
        final long currentScopeKey = scopes.poll();

        if (interruptedScopes.contains(currentScopeKey)) {
          // if we reached a scope that was already interrupted, we can skip evaluating its
          // conditionals and its children since they are also interrupted
          break;
        }

        visitSubscriptions(
            variableEvent,
            currentScopeKey,
            triggeredSubscriptionKeys,
            interruptedScopes,
            cachedExpressions);

        if (!interruptedScopes.contains(currentScopeKey)) {
          // not interrupted, so continue traversing to child scopes
          elementInstanceState.forEachChild(
              currentScopeKey, -1, (childKey, ignore) -> scopes.add(childKey));
        }
      }
    }
  }

  /**
   * This method has a side effect. It updates triggeredSubscriptionKeys set, interruptedScopes set,
   * and cachedExpressions map.
   *
   * @param variableEvent the variable event
   * @param currentScopeKey the current scope key to evaluate subscriptions for
   * @param triggeredSubscriptionKeys triggered subscription keys to avoid triggering the same
   *     subscription multiple times
   * @param interruptedScopes interrupted scopes to optimize further evaluations
   * @param cachedExpressions cached expressions to avoid parsing the same expression multiple times
   */
  private void visitSubscriptions(
      final VariableEvent variableEvent,
      final long currentScopeKey,
      final Set<Long> triggeredSubscriptionKeys,
      final Set<Long> interruptedScopes,
      final Map<Long, Expression> cachedExpressions) {
    conditionSubscriptionState.visitByScopeKey(
        currentScopeKey,
        subscription -> {
          final var subscriptionKey = subscription.getKey();
          final var record = subscription.getRecord();

          if (triggeredSubscriptionKeys.contains(subscriptionKey)) {
            // already triggered, so continue with next subscription
            return true;
          }

          // apply variableNames and variableEvents filter
          if (!variableEvent.matchesFilters(
              record.getVariableNames(), record.getVariableEvents())) {
            // no match, so continue with next subscription
            return true;
          }

          if (shouldTrigger(subscription, currentScopeKey, cachedExpressions)) {
            commandWriter.appendFollowUpCommand(
                subscriptionKey, ConditionalSubscriptionIntent.TRIGGER, record);
            triggeredSubscriptionKeys.add(subscriptionKey);

            if (record.isInterrupting()) {
              // mark the scope as interrupted to optimize further evaluations
              interruptedScopes.add(currentScopeKey);
              // stop traversing further subscriptions
              return false;
            }
          }

          return true;
        });
  }

  private boolean shouldTrigger(
      final ConditionalSubscription subscription,
      final long currentScopeKey,
      final Map<Long, Expression> cachedExpressions) {
    final var subscriptionKey = subscription.getKey();
    final var record = subscription.getRecord();

    final Expression conditionExpression =
        cachedExpressions.computeIfAbsent(
            subscriptionKey, key -> expressionLanguage.parseExpression(record.getCondition()));
    final var evaluation =
        expressionProcessor.evaluateBooleanExpression(
            conditionExpression, currentScopeKey, record.getTenantId());

    return evaluation.isRight() && evaluation.get().equals(true);
  }

  private boolean isConditionalSubscriptionExist(final long processDefinitionKey) {
    // no conditional subscriptions for the process definition, so skip evaluation
    return conditionSubscriptionState.exists(processDefinitionKey);
  }

  public record VariableEvent(long scopeKey, VariableIntent intent, VariableRecord record) {
    private static final Map<VariableIntent, String> INTENT_TO_EVENT_MAP =
        Map.of(
            VariableIntent.CREATED, "create",
            VariableIntent.UPDATED, "update");

    boolean matchesFilters(
        final List<String> variableNamesFilter, final List<String> variableEventsFilter) {
      return matchesNameFilter(variableNamesFilter) && matchesEventFilter(variableEventsFilter);
    }

    boolean matchesEventFilter(final List<String> variableEventsFilter) {
      if (variableEventsFilter.isEmpty()) {
        return true;
      }
      return variableEventsFilter.contains(INTENT_TO_EVENT_MAP.get(intent));
    }

    boolean matchesNameFilter(final List<String> variableNamesFilter) {
      if (variableNamesFilter.isEmpty()) {
        return true;
      }
      return variableNamesFilter.contains(record.getName());
    }
  }
}
