/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import static io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableConditional;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSignal;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerCheckScheduler;
import io.camunda.zeebe.engine.state.conditional.ConditionalSubscription;
import io.camunda.zeebe.engine.state.immutable.ConditionalSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SignalSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.engine.state.signal.SignalSubscription;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.List;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CatchEventBehavior {

  private static final Logger LOG = LoggerFactory.getLogger(CatchEventBehavior.class);

  private final ExpressionProcessor expressionProcessor;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final RoutingInfo routingInfo;
  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final TypedCommandWriter commandWriter;

  private final ProcessMessageSubscriptionState processMessageSubscriptionState;
  private final TimerInstanceState timerInstanceState;
  private final ProcessState processState;
  private final SignalSubscriptionState signalSubscriptionState;
  private final ConditionalSubscriptionState conditionalSubscriptionState;

  private final ProcessMessageSubscriptionRecord subscription =
      new ProcessMessageSubscriptionRecord();
  private final TimerRecord timerRecord = new TimerRecord();
  private final SignalSubscriptionRecord signalSubscription = new SignalSubscriptionRecord();
  private final ConditionalSubscriptionRecord conditionalSubscription =
      new ConditionalSubscriptionRecord();
  private final DueDateTimerCheckScheduler timerChecker;
  private final KeyGenerator keyGenerator;
  private final InstantSource clock;
  private final TransientPendingSubscriptionState transientProcessMessageSubscriptionState;

  public CatchEventBehavior(
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final ExpressionProcessor expressionProcessor,
      final SubscriptionCommandSender subscriptionCommandSender,
      final Writers writers,
      final DueDateTimerCheckScheduler timerChecker,
      final RoutingInfo routingInfo,
      final InstantSource clock,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState) {
    this.expressionProcessor = expressionProcessor;
    this.subscriptionCommandSender = subscriptionCommandSender;
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
    commandWriter = writers.command();
    this.routingInfo = routingInfo;

    timerInstanceState = processingState.getTimerState();
    processMessageSubscriptionState = processingState.getProcessMessageSubscriptionState();
    processState = processingState.getProcessState();
    signalSubscriptionState = processingState.getSignalSubscriptionState();
    conditionalSubscriptionState = processingState.getConditionalSubscriptionState();

    this.keyGenerator = keyGenerator;
    this.timerChecker = timerChecker;
    this.clock = clock;
    this.transientProcessMessageSubscriptionState = transientProcessMessageSubscriptionState;
  }

  /**
   * Unsubscribe from all events in the scope of the element instance.
   *
   * @param elementInstanceKey the element instance key to subscript from
   */
  public void unsubscribeFromEvents(final long elementInstanceKey) {
    unsubscribeFromEvents(elementInstanceKey, elementId -> true);
  }

  /**
   * Unsubscribe from all event subprocesses in the scope of the element instance. Ignores other
   * event subscriptions in the scope.
   *
   * @param context the context to subscript from
   */
  public void unsubscribeEventSubprocesses(final BpmnElementContext context) {
    unsubscribeFromEvents(
        context.getElementInstanceKey(), elementId -> isEventSubprocess(context, elementId));
  }

  private boolean isEventSubprocess(
      final BpmnElementContext context, final DirectBuffer elementId) {

    final var element =
        processState.getFlowElement(
            context.getProcessDefinitionKey(),
            context.getTenantId(),
            elementId,
            ExecutableFlowElement.class);

    return element.getElementType() == BpmnElementType.START_EVENT
        && element.getFlowScope().getElementType() == BpmnElementType.EVENT_SUB_PROCESS;
  }

  /**
   * Unsubscribe from all events in the scope of the element instance that matches the given filter.
   * Ignore other event subscriptions that don't match the filter.
   *
   * @param elementInstanceKey the element instance key to subscript from
   * @param elementIdFilter the filter for events to unsubscribe
   */
  private void unsubscribeFromEvents(
      final long elementInstanceKey, final Predicate<DirectBuffer> elementIdFilter) {

    unsubscribeFromTimerEvents(elementInstanceKey, elementIdFilter);
    unsubscribeFromMessageEvents(
        elementInstanceKey, sub -> elementIdFilter.test(sub.getRecord().getElementIdBuffer()));
    unsubscribeFromSignalEvents(elementInstanceKey, elementIdFilter);
    unsubscribeFromConditionalEvents(elementInstanceKey, elementIdFilter);
  }

  private void unsubscribeFromConditionalEvents(
      final long elementInstanceKey, final Predicate<DirectBuffer> elementIdFilter) {
    unsubscribeFromConditionalEventsBySubscriptionFilter(
        elementInstanceKey,
        subscription -> elementIdFilter.test(subscription.getRecord().getCatchEventIdBuffer()));
  }

  public void unsubscribeFromConditionalEventsBySubscriptionFilter(
      final long elementInstanceKey,
      final Predicate<ConditionalSubscription> conditionalSubscriptionFilter) {
    conditionalSubscriptionState.visitByScopeKey(
        elementInstanceKey,
        subscription -> {
          if (conditionalSubscriptionFilter.test(subscription)) {
            stateWriter.appendFollowUpEvent(
                subscription.getKey(),
                ConditionalSubscriptionIntent.DELETED,
                subscription.getRecord());
          }
          return true;
        });
  }

  /**
   * Subscribes to all events of the given supplier.
   *
   * @param context the context of the element instance that subscribes to events
   * @param supplier the supplier of catch events to subscribe to, typically the element of the
   *     element instance that subscribes to events
   * @return either a failure or nothing
   */
  public Either<Failure, Void> subscribeToEvents(
      final BpmnElementContext context, final ExecutableCatchEventSupplier supplier) {
    return subscribeToEvents(context, supplier, executableCatchEvent -> true, catchEvent -> true);
  }

  /**
   * Subscribes to all events of the given supplier that match the given catch event id filter.
   *
   * <p>Note that the filter provides access to the catch event as well as the result of evaluating
   * its expressions. This allows for more complex filters that depend on both the catch event and
   * the result of evaluating its expressions.
   *
   * @param context the context of the element instance that subscribes to events
   * @param supplier the supplier of catch events to subscribe to, typically the element of the
   *     element instance that subscribes to events
   * @param filterBeforeEvaluation the filter for catch events to subscribe to. Only events that
   *     match the filter are subscribed to. This filter is applied before evaluating the catch
   *     event's expressions. This is especially useful for filtering catch events that doesn't
   *     require an expression evaluation.
   * @param filterAfterEvaluation the filter for catch events to subscribe to. Only events that
   *     match the filter are subscribed to. This filter is applied after evaluating the catch
   *     event's expressions.
   * @return either a failure or nothing
   */
  public Either<Failure, Void> subscribeToEvents(
      final BpmnElementContext context,
      final ExecutableCatchEventSupplier supplier,
      final Predicate<ExecutableCatchEvent> filterBeforeEvaluation,
      final Predicate<CatchEvent> filterAfterEvaluation) {
    final var evaluationResults =
        supplier.getEvents().stream()
            .filter(event -> event.isTimer() || event.isMessage() || event.isSignal())
            .filter(filterBeforeEvaluation)
            .map(event -> evalExpressions(expressionProcessor, event, context))
            .filter(
                result ->
                    result.map(CatchEvent::new).map(filterAfterEvaluation::test).getOrElse(true))
            .collect(Either.collectorFoldingLeft());

    evaluationResults.ifRight(
        results -> {
          subscribeToMessageEvents(context, results);
          subscribeToTimerEvents(context, results);
          subscribeToSignalEvents(context, results);
        });

    // conditional events don't require expression evaluation for subscription
    // condition expressions are stored as-is and evaluated when a variable changes
    subscribeToConditionalEvents(context, supplier);

    return evaluationResults.map(r -> null);
  }

  private Either<Failure, EvalResult> evalExpressions(
      final ExpressionProcessor ep,
      final ExecutableCatchEvent event,
      final BpmnElementContext context) {
    return Either.<Failure, OngoingEvaluation>right(new OngoingEvaluation(ep, event, context))
        .flatMap(this::evaluateMessageName)
        .flatMap(this::evaluateCorrelationKey)
        .flatMap(this::evaluateTimer)
        .flatMap(this::evaluateSignalName)
        .map(OngoingEvaluation::getResult);
  }

  private Either<Failure, OngoingEvaluation> evaluateMessageName(
      final OngoingEvaluation evaluation) {
    final var event = evaluation.event();

    if (!event.isMessage()) {
      return Either.right(evaluation);
    }
    final var scopeKey = evaluation.context().getElementInstanceKey();
    final var tenantId = evaluation.context().getTenantId();
    final ExecutableMessage message = event.getMessage();
    final Expression messageNameExpression = message.getMessageNameExpression();
    return evaluation
        .expressionProcessor()
        .evaluateStringExpression(messageNameExpression, scopeKey, tenantId)
        .map(BufferUtil::wrapString)
        .map(evaluation::recordMessageName);
  }

  private Either<Failure, OngoingEvaluation> evaluateCorrelationKey(
      final OngoingEvaluation evaluation) {

    final var event = evaluation.event();
    final var context = evaluation.context();
    if (!event.isMessage()) {
      return Either.right(evaluation);
    }
    final var expression = event.getMessage().getCorrelationKeyExpression();
    final long scopeKey =
        event.getElementType() == BpmnElementType.BOUNDARY_EVENT
            ? context.getFlowScopeKey()
            : context.getElementInstanceKey();
    final String tenantId = context.getTenantId();
    return evaluation
        .expressionProcessor()
        .evaluateMessageCorrelationKeyExpression(expression, scopeKey, tenantId)
        .map(BufferUtil::wrapString)
        .map(evaluation::recordCorrelationKey)
        .mapLeft(f -> new Failure(f.getMessage(), f.getErrorType(), scopeKey));
  }

  private Either<Failure, OngoingEvaluation> evaluateTimer(final OngoingEvaluation evaluation) {
    final var event = evaluation.event();
    final var context = evaluation.context();
    if (!event.isTimer()) {
      return Either.right(evaluation);
    }
    return event
        .getTimerFactory()
        .apply(
            evaluation.expressionProcessor(),
            context.getElementInstanceKey(),
            context.getTenantId())
        .map(evaluation::recordTimer);
  }

  private Either<Failure, OngoingEvaluation> evaluateSignalName(
      final OngoingEvaluation evaluation) {
    final var event = evaluation.event();

    if (!event.isSignal()) {
      return Either.right(evaluation);
    }
    final var scopeKey = evaluation.context().getElementInstanceKey();
    final var tenantId = evaluation.context().getTenantId();
    final ExecutableSignal signal = event.getSignal();
    final Expression signalNameExpression = signal.getSignalNameExpression();
    return evaluation
        .expressionProcessor()
        .evaluateStringExpression(signalNameExpression, scopeKey, tenantId)
        .map(BufferUtil::wrapString)
        .map(evaluation::recordSignalName);
  }

  private void subscribeToMessageEvents(
      final BpmnElementContext context, final List<EvalResult> results) {
    results.stream()
        .filter(EvalResult::isMessage)
        .forEach(result -> subscribeToMessageEvent(context, result));
  }

  private void subscribeToMessageEvent(final BpmnElementContext context, final EvalResult result) {
    final var event = result.event;
    final var correlationKey = result.correlationKey;
    final var messageName = result.messageName;

    final long processInstanceKey = context.getProcessInstanceKey();
    final long rootProcessInstanceKey = context.getRootProcessInstanceKey();
    final DirectBuffer bpmnProcessId = cloneBuffer(context.getBpmnProcessId());
    final long elementInstanceKey = context.getElementInstanceKey();
    final long processDefinitionKey = context.getProcessDefinitionKey();

    final int subscriptionPartitionId = routingInfo.partitionForCorrelationKey(correlationKey);

    subscription.setSubscriptionPartitionId(subscriptionPartitionId);
    subscription.setMessageName(messageName);
    subscription.setElementInstanceKey(elementInstanceKey);
    subscription.setProcessInstanceKey(processInstanceKey);
    subscription.setProcessDefinitionKey(processDefinitionKey);
    subscription.setBpmnProcessId(bpmnProcessId);
    subscription.setCorrelationKey(correlationKey);
    subscription.setElementId(event.getId());
    subscription.setInterrupting(event.isInterrupting());
    subscription.setTenantId(context.getTenantId());
    subscription.setRootProcessInstanceKey(rootProcessInstanceKey);

    final var subscriptionKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        subscriptionKey, ProcessMessageSubscriptionIntent.CREATING, subscription);

    sendOpenMessageSubscription(
        subscriptionPartitionId,
        processInstanceKey,
        elementInstanceKey,
        processDefinitionKey,
        bpmnProcessId,
        messageName,
        correlationKey,
        event.isInterrupting(),
        context.getTenantId());

    final String subscriptionMessageName = subscription.getMessageName();
    final String tenantId = subscription.getTenantId();
    final var lastSentTime = clock.millis();

    // update transient state in a side-effect to ensure that these changes only take effect after
    // the command has been successfully processed
    sideEffectWriter.appendSideEffect(
        () -> {
          transientProcessMessageSubscriptionState.update(
              new PendingSubscription(elementInstanceKey, subscriptionMessageName, tenantId),
              lastSentTime);
          return true;
        });
  }

  private void subscribeToTimerEvents(
      final BpmnElementContext context, final List<EvalResult> results) {
    results.stream()
        .filter(EvalResult::isTimer)
        .forEach(
            result -> {
              final var event = result.event;
              final var timer = result.timer;
              subscribeToTimerEvent(
                  context.getElementInstanceKey(),
                  context.getProcessInstanceKey(),
                  context.getProcessDefinitionKey(),
                  event.getId(),
                  context.getTenantId(),
                  timer);
            });
  }

  public void subscribeToTimerEvent(
      final long elementInstanceKey,
      final long processInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer handlerNodeId,
      final String tenantId,
      final Timer timer) {
    final long dueDate = timer.getDueDate(clock.millis());
    timerRecord.reset();
    timerRecord
        .setRepetitions(timer.getRepetitions())
        .setDueDate(dueDate)
        .setElementInstanceKey(elementInstanceKey)
        .setProcessInstanceKey(processInstanceKey)
        .setTargetElementId(handlerNodeId)
        .setProcessDefinitionKey(processDefinitionKey)
        .setTenantId(tenantId);

    sideEffectWriter.appendSideEffect(
        () -> {
          /* timerChecker implements onRecovered to recover from restart, so no need to schedule
          this in TimerCreatedApplier.*/
          timerChecker.scheduleTimer(dueDate);
          return true;
        });

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), TimerIntent.CREATED, timerRecord);
  }

  private void subscribeToSignalEvents(
      final BpmnElementContext context, final List<EvalResult> results) {
    results.stream()
        .filter(EvalResult::isSignal)
        .forEach(result -> subscribeToSignalEvent(context, result));
  }

  private void subscribeToSignalEvent(final BpmnElementContext context, final EvalResult result) {
    final var event = result.event;
    final var signalName = result.signalName;

    signalSubscription.reset();
    signalSubscription
        .setSignalName(signalName)
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setBpmnProcessId(context.getBpmnProcessId())
        .setCatchEventInstanceKey(context.getElementInstanceKey())
        .setCatchEventId(event.getId())
        .setTenantId(context.getTenantId());

    final var subscriptionKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        subscriptionKey, SignalSubscriptionIntent.CREATED, signalSubscription);
  }

  private void subscribeToConditionalEvents(
      final BpmnElementContext context, final ExecutableCatchEventSupplier supplier) {
    supplier.getEvents().stream()
        .filter(ExecutableCatchEvent::isConditional)
        .forEach(event -> subscribeToConditionalEvent(context, event));
  }

  /**
   * Closest scope key for different elements \(equals to context.getElementInstanceKey\):
   *
   * <ul>
   *   <li>Boundary event &rarr; attached activity
   *   <li>Intermediate catch event &rarr; element itself
   *   <li>Event subprocess start event &rarr; flow scope that is enclosing the event subprocess
   *   <li>Root level start event &rarr; not handled here, will be evaluated through endpoint call
   * </ul>
   */
  private void subscribeToConditionalEvent(
      final BpmnElementContext context, final ExecutableCatchEvent event) {
    final var conditional = event.getConditional();

    conditionalSubscription.reset();
    conditionalSubscription
        .setScopeKey(context.getElementInstanceKey())
        .setElementInstanceKey(context.getElementInstanceKey())
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setBpmnProcessId(context.getBpmnProcessId())
        .setCatchEventId(event.getId())
        .setInterrupting(event.isInterrupting())
        .setCondition(BufferUtil.wrapString(conditional.getCondition()))
        .setVariableNames(conditional.getVariableNames())
        .setVariableEvents(conditional.getVariableEvents())
        .setTenantId(context.getTenantId());

    final var subscriptionKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        subscriptionKey, ConditionalSubscriptionIntent.CREATED, conditionalSubscription);

    // Evaluate the condition immediately after creating the subscription.
    // If it evaluates to true, the conditional event is triggered right away.
    tryEvaluateSubscription(conditional, subscriptionKey, conditionalSubscription);
  }

  private void tryEvaluateSubscription(
      final ExecutableConditional conditional,
      final long subscriptionKey,
      final ConditionalSubscriptionRecord subscriptionRecord) {
    final var scopeKey = subscriptionRecord.getScopeKey();
    final String tenantId = subscriptionRecord.getTenantId();
    final Expression conditionExpression = conditional.getConditionExpression();

    final Either<Failure, Boolean> evaluation =
        expressionProcessor.evaluateBooleanExpression(conditionExpression, scopeKey, tenantId);
    if (evaluation.isRight()) {
      if (Boolean.TRUE.equals(evaluation.get())) {
        commandWriter.appendFollowUpCommand(
            subscriptionKey, ConditionalSubscriptionIntent.TRIGGER, subscriptionRecord);
      }
    }
  }

  public void unsubscribeFromSignalEventsBySubscriptionFilter(
      final long elementInstanceKey, final Predicate<SignalSubscription> signalSubscriptionFilter) {
    signalSubscriptionState.visitByElementInstanceKey(
        elementInstanceKey,
        subscription -> {
          if (signalSubscriptionFilter.test(subscription)) {
            stateWriter.appendFollowUpEvent(
                subscription.getKey(), SignalSubscriptionIntent.DELETED, subscription.getRecord());
          }
        });
  }

  private void unsubscribeFromSignalEvents(
      final long elementInstanceKey, final Predicate<DirectBuffer> elementIdFilter) {
    unsubscribeFromSignalEventsBySubscriptionFilter(
        elementInstanceKey,
        signal -> elementIdFilter.test(signal.getRecord().getCatchEventIdBuffer()));
  }

  public void unsubscribeFromTimerEventsByInstanceFilter(
      final long elementInstanceKey, final Predicate<TimerInstance> timerInstanceFilter) {
    timerInstanceState.forEachTimerForElementInstance(
        elementInstanceKey,
        timer -> {
          if (timerInstanceFilter.test(timer)) {
            unsubscribeFromTimerEvent(timer);
          }
        });
  }

  private void unsubscribeFromTimerEvents(
      final long elementInstanceKey, final Predicate<DirectBuffer> elementIdFilter) {
    unsubscribeFromTimerEventsByInstanceFilter(
        elementInstanceKey, timer -> elementIdFilter.test(timer.getHandlerNodeId()));
  }

  public void unsubscribeFromTimerEvent(final TimerInstance timer) {
    timerRecord.reset();
    timerRecord
        .setElementInstanceKey(timer.getElementInstanceKey())
        .setProcessInstanceKey(timer.getProcessInstanceKey())
        .setDueDate(timer.getDueDate())
        .setRepetitions(timer.getRepetitions())
        .setTargetElementId(timer.getHandlerNodeId())
        .setProcessDefinitionKey(timer.getProcessDefinitionKey())
        .setTenantId(timer.getTenantId());

    stateWriter.appendFollowUpEvent(timer.getKey(), TimerIntent.CANCELED, timerRecord);
  }

  public void unsubscribeFromMessageEvents(
      final long elementInstanceKey, final Predicate<ProcessMessageSubscription> filter) {
    processMessageSubscriptionState.visitElementSubscriptions(
        elementInstanceKey,
        subscription -> {
          if (filter.test(subscription)) {
            unsubscribeFromMessageEvent(subscription);
          }
          return true;
        });
  }

  private void unsubscribeFromMessageEvent(final ProcessMessageSubscription subscription) {

    final DirectBuffer messageName = cloneBuffer(subscription.getRecord().getMessageNameBuffer());
    final String messageNameString = subscription.getRecord().getMessageName();
    final int subscriptionPartitionId = subscription.getRecord().getSubscriptionPartitionId();
    final long processInstanceKey = subscription.getRecord().getProcessInstanceKey();
    final long elementInstanceKey = subscription.getRecord().getElementInstanceKey();
    final long processDefinitionKey = subscription.getRecord().getProcessDefinitionKey();
    final String tenantId = subscription.getRecord().getTenantId();

    stateWriter.appendFollowUpEvent(
        subscription.getKey(), ProcessMessageSubscriptionIntent.DELETING, subscription.getRecord());

    sendCloseMessageSubscriptionCommand(
        subscriptionPartitionId,
        processInstanceKey,
        elementInstanceKey,
        processDefinitionKey,
        messageName,
        subscription.getRecord().getTenantId());
    final var lastSentTime = clock.millis();

    // update transient state in a side-effect to ensure that these changes only take effect after
    // the command has been successfully processed
    sideEffectWriter.appendSideEffect(
        () -> {
          transientProcessMessageSubscriptionState.update(
              new PendingSubscription(elementInstanceKey, messageNameString, tenantId),
              lastSentTime);
          return true;
        });
  }

  private boolean sendCloseMessageSubscriptionCommand(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer messageName,
      final String tenantId) {
    return subscriptionCommandSender.closeMessageSubscription(
        subscriptionPartitionId,
        processInstanceKey,
        elementInstanceKey,
        processDefinitionKey,
        messageName,
        tenantId);
  }

  private boolean sendOpenMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final boolean closeOnCorrelate,
      final String tenantId) {
    return subscriptionCommandSender.openMessageSubscription(
        subscriptionPartitionId,
        processInstanceKey,
        elementInstanceKey,
        processDefinitionKey,
        bpmnProcessId,
        messageName,
        correlationKey,
        closeOnCorrelate,
        tenantId);
  }

  public record CatchEvent(ExecutableCatchEvent element, DirectBuffer messageName, Timer timer) {

    private CatchEvent(final EvalResult result) {
      this(result.event(), result.messageName(), result.timer());
    }
  }

  /**
   * Transient helper object that captures the information necessary to evaluate important
   * expressions for a message, and to capture intermediate results of the evaluation
   */
  private static class OngoingEvaluation {
    private final ExpressionProcessor expressionProcessor;
    private final ExecutableCatchEvent event;
    private final BpmnElementContext context;
    private DirectBuffer messageName;
    private DirectBuffer correlationKey;
    private Timer timer;
    private DirectBuffer signalName;

    public OngoingEvaluation(
        final ExpressionProcessor expressionProcessor,
        final ExecutableCatchEvent event,
        final BpmnElementContext context) {
      this.expressionProcessor = expressionProcessor;
      this.event = event;
      this.context = context;
    }

    private ExpressionProcessor expressionProcessor() {
      return expressionProcessor;
    }

    private ExecutableCatchEvent event() {
      return event;
    }

    private BpmnElementContext context() {
      return context;
    }

    public OngoingEvaluation recordMessageName(final DirectBuffer messageName) {
      this.messageName = messageName;
      return this;
    }

    public OngoingEvaluation recordCorrelationKey(final DirectBuffer correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    public OngoingEvaluation recordTimer(final Timer timer) {
      this.timer = timer;
      return this;
    }

    public OngoingEvaluation recordSignalName(final DirectBuffer signalName) {
      this.signalName = signalName;
      return this;
    }

    EvalResult getResult() {
      return new EvalResult(event, messageName, correlationKey, timer, signalName);
    }
  }

  private record EvalResult(
      ExecutableCatchEvent event,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      Timer timer,
      DirectBuffer signalName) {

    public boolean isMessage() {
      return event.isMessage();
    }

    public boolean isTimer() {
      return event.isTimer();
    }

    public boolean isSignal() {
      return event.isSignal();
    }
  }
}
