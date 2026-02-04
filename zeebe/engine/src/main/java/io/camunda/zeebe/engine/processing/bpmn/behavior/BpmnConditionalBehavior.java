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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
   * <p>Stop the traversal of the scope when either of the following occurs:
   *
   * <ul>
   *   <li>An interrupting subscription is triggered.
   *   <li>No more child scopes exist.
   * </ul>
   *
   * <p>Before evaluating a scope's conditional subscriptions, apply the {@code variableNamesFilter}
   * and {@code variableEventsFilter} filters. Only if there is a match, evaluate the condition.
   * These filters are optional. If they are not set, apply the filter as passed.
   *
   * @param processInstanceKey the process instance key to check if subscriptions exist for
   * @param variableEvents the list of variable events to evaluate conditionals for
   */
  public void evaluateConditionals(
      final long processInstanceKey, final List<VariableEvent> variableEvents) {
    if (!isConditionalSubscriptionExist(processInstanceKey)) {
      return;
    }

    // group variable events by their scope key to optimize the evaluation of conditionals by scope
    final Map<Long, List<VariableEvent>> scopeKeyToVariableEvents =
        variableEvents.stream()
            .collect(Collectors.groupingBy(VariableEvent::scopeKey, Collectors.toList()));
    // merge variable events from parent scopes into their child scopes and get the child to parent
    // mapping for calculating depth
    final Map<Long, Long> childToParent =
        mergeVariableEventsToChildScopes(scopeKeyToVariableEvents);
    // calculate the depth function for sorting scopes from top to bottom
    final ScopeToDepthFunction depthFunction =
        calculateDepthFunction(scopeKeyToVariableEvents.size(), childToParent);
    // order scopes from top to bottom based on their depth
    final List<Long> orderedScopes = new ArrayList<>(scopeKeyToVariableEvents.keySet());
    orderedScopes.sort(Comparator.comparingInt(depthFunction::calculateDepth));

    // optimize the traversal by storing interrupted scopes
    final Set<Long> interruptedScopes = new HashSet<>();
    for (final long scopeKey : orderedScopes) {
      final List<VariableEvent> scopedVariableEvents = scopeKeyToVariableEvents.get(scopeKey);

      if (interruptedScopes.contains(childToParent.get(scopeKey))) {
        // if the parent scope is interrupted, then we can skip evaluating this scope and mark it as
        // interrupted as well
        interruptedScopes.add(scopeKey);
        continue;
      }

      visitSubscriptions(scopeKey, scopedVariableEvents, interruptedScopes);
    }
  }

  /**
   * Merges variable events from parent scopes into their child scopes. This way, when we evaluate a
   * scope's conditionals, we have all variable events from its scope and ancestor scopes.
   *
   * <p>Please note that this method has a side effect. It updates the given
   * scopeKeyToVariableEvents map by adding parent variable events into their child scopes.
   *
   * @param scopeKeyToVariableEvents the map of scope keys to their variable events, which will be
   *     updated by merging parent scope
   * @return a map of child scope keys to their parent scope keys, which can be used to calculate
   *     the depth
   */
  private Map<Long, Long> mergeVariableEventsToChildScopes(
      final Map<Long, List<VariableEvent>> scopeKeyToVariableEvents) {
    // copy the map to avoid concurrent modification while traversing and merging
    final var copyScopeKeyToVariableEvents = new HashMap<>(scopeKeyToVariableEvents);
    final Map<Long, Long> childToParent = new HashMap<>();
    for (final var entry : copyScopeKeyToVariableEvents.entrySet()) {
      final long scopeKey = entry.getKey();
      final List<VariableEvent> variableEventsForScope = entry.getValue();

      final var descendantScopeKeys = new ArrayDeque<Long>();
      descendantScopeKeys.add(scopeKey);
      while (!descendantScopeKeys.isEmpty()) {
        final long currentScopeKey = descendantScopeKeys.poll();

        elementInstanceState.forEachChildKey(
            currentScopeKey,
            childScopeKey -> {
              // record parent once
              childToParent.putIfAbsent(childScopeKey, currentScopeKey);
              // propagate variable events from parent scope into child's scope
              scopeKeyToVariableEvents
                  .computeIfAbsent(childScopeKey, k -> new ArrayList<>())
                  .addAll(variableEventsForScope);

              descendantScopeKeys.add(childScopeKey);
              return true;
            });
      }
    }

    return childToParent;
  }

  /**
   * Calculates the depth function for the given scopes and their parent-child relationships
   * recursively. The depth is defined as the number of edges on the path from the scope to the root
   * scope. Root scopes have a depth of 0.
   *
   * <p>Performance behavior:
   *
   * <ul>
   *   <li>Best case: scopes are already sorted top to bottom
   *   <li>Worst case: scopes are already sorted bottom to top
   * </ul>
   *
   * @param scopeCount the number of scopes to calculate the depth function for, used for
   *     initializing the cache map to avoid resizing
   * @param childToParent the map of child scope keys to their parent scope keys, used to calculate
   *     the depth of each scope
   * @return a ScopeToDepthFunction that can calculate the depth of any scope key based on the
   *     provided parent-child relationships, with memoization to optimize repeated calculations
   */
  private static ScopeToDepthFunction calculateDepthFunction(
      final int scopeCount, final Map<Long, Long> childToParent) {
    // cache to prevent more recursions
    final Map<Long, Integer> scopeKeyToDepthCache = new HashMap<>(scopeCount);
    return new ScopeToDepthFunction() {
      @Override
      public int calculateDepth(final long scopeKey) {
        final Integer cached = scopeKeyToDepthCache.get(scopeKey);
        if (cached != null) {
          return cached;
        }

        final Long parent = childToParent.get(scopeKey);
        final int depth = (parent == null) ? 0 : calculateDepth(parent) + 1;
        scopeKeyToDepthCache.put(scopeKey, depth);

        return depth;
      }
    };
  }

  /**
   * Visits the conditional subscriptions of the given scope key and triggers them if their
   * condition is satisfied by the given variable events. If a subscription is triggered and is
   * interrupting, mark the scope as interrupted by adding it to the interruptedScopes set, and stop
   * visiting further
   *
   * <p>This method has a side effect; it updates interruptedScopes set.
   *
   * @param variableEvents the variable events to apply filters for before evaluating the condition
   * @param currentScopeKey the current scope key to evaluate subscriptions for
   * @param interruptedScopes interrupted scopes to optimize further evaluations
   */
  private void visitSubscriptions(
      final long currentScopeKey,
      final List<VariableEvent> variableEvents,
      final Set<Long> interruptedScopes) {
    conditionSubscriptionState.visitByScopeKey(
        currentScopeKey,
        subscription -> {
          final var subscriptionKey = subscription.getKey();
          final var record = subscription.getRecord();
          Expression conditionExpression = null;

          for (final VariableEvent variableEvent : variableEvents) {
            // apply variableNames and variableEvents filter
            if (!variableEvent.matchesFilters(
                record.getVariableNames(), record.getVariableEvents())) {
              // no match, so continue with the next event
              continue;
            }

            // only parse when needed and reuse the parsed expression for the next events of the
            // same subscription
            if (conditionExpression == null) {
              conditionExpression = expressionLanguage.parseExpression(record.getCondition());
            }

            if (shouldTrigger(subscription, currentScopeKey, conditionExpression)) {
              commandWriter.appendFollowUpCommand(
                  subscriptionKey, ConditionalSubscriptionIntent.TRIGGER, record);

              if (record.isInterrupting()) {
                // mark the scope as interrupted to optimize further evaluations
                interruptedScopes.add(currentScopeKey);
                // stop traversing further subscriptions
                return false;
              }

              // this subscription is triggered, continue with next subscription
              return true;
            }
          }

          return true;
        });
  }

  private boolean shouldTrigger(
      final ConditionalSubscription subscription,
      final long currentScopeKey,
      final Expression conditionExpression) {
    final var evaluation =
        expressionProcessor.evaluateBooleanExpression(
            conditionExpression, currentScopeKey, subscription.getRecord().getTenantId());

    return evaluation.isRight() && evaluation.get().equals(true);
  }

  private boolean isConditionalSubscriptionExist(final long processInstanceKey) {
    return conditionSubscriptionState.exists(processInstanceKey);
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

  @FunctionalInterface
  public interface ScopeToDepthFunction {

    /**
     * Calculates the depth of the given scope key. The depth is defined as the number of edges on
     * the path from the scope to the root scope. Root scopes have a depth of 0.
     *
     * @param scopeKey the scope key to calculate the depth for
     * @return the depth of the given scope key
     */
    int calculateDepth(long scopeKey);
  }
}
