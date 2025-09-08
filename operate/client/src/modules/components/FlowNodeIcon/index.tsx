/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {getBoundaryEventType} from 'modules/bpmn-js/utils/getBoundaryEventType';
import {getEventType} from 'modules/bpmn-js/utils/getEventType';
import {getMultiInstanceType} from 'modules/bpmn-js/utils/getMultiInstanceType';
import {isEventSubProcess} from 'modules/bpmn-js/utils/isEventSubProcess';
import {isInterruptingEvent} from 'modules/bpmn-js/utils/isInterruptingEvent';
import {type FlowNodeInstance} from 'modules/stores/flowNodeInstance';
import {SVGIcon} from './styled';

import FlowNodeProcess from 'modules/components/Icon/flow-node-process-root.svg?react';

/**
 * Gateway Imports
 */
import FlowNodeGatewayInclusive from 'modules/components/Icon/flow-node-gateway-inclusive-or.svg?react';
import FlowNodeGatewayExclusive from 'modules/components/Icon/flow-node-gateway-exclusive.svg?react';
import FlowNodeGatewayParallel from 'modules/components/Icon/flow-node-gateway-parallel.svg?react';
import FlowNodeGatewayEventBased from 'modules/components/Icon/flow-node-gateway-event-based.svg?react';

/**
 * Task Imports
 */

import FlowNodeTask from 'modules/components/Icon/flow-node-task-undefined.svg?react';
import FlowNodeTaskService from 'modules/components/Icon/flow-node-task-service.svg?react';
import FlowNodeTaskReceive from 'modules/components/Icon/flow-node-task-receive.svg?react';
import FlowNodeTaskSend from 'modules/components/Icon/flow-node-task-send.svg?react';
import FlowNodeTaskSubProcess from 'modules/components/Icon/flow-node-subprocess-embedded.svg?react';
import FlowNodeTaskSubProcessAdhoc from 'modules/components/Icon/flow-node-subprocess-adhoc.svg?react';
import FlowNodeTaskSubProcessAdhocInnerInstance from 'modules/components/Icon/flow-node-subprocess-adhoc-inner-instance.svg?react';
import FlowNodeTaskMulti from 'modules/components/Icon/flow-node-multi-instance-parallel.svg?react';
import FlowNodeTaskParallel from 'modules/components/Icon/flow-node-multi-instance-sequential.svg?react';
import FlowNodeCallActivity from 'modules/components/Icon/flow-node-call-activity.svg?react';
import FlowNodeTaskUser from 'modules/components/Icon/flow-node-task-user.svg?react';
import FlowNodeTaskBusinessRule from 'modules/components/Icon/flow-node-task-business-rule.svg?react';
import FlowNodeTaskScript from 'modules/components/Icon/flow-node-task-script.svg?react';
import FlowNodeTaskManual from 'modules/components/Icon/flow-node-task-manual.svg?react';

/**
 * Event Imports
 */

import FlowNodeEventStart from 'modules/components/Icon/flow-node-event-start.svg?react';
import FlowNodeEventEnd from 'modules/components/Icon/flow-node-event-end.svg?react';
import FlowNodeEventIntermediateThrow from 'modules/components/Icon/flow-node-event-intermediate-none.svg?react';

import FlowNodeEventMessageStart from 'modules/components/Icon/flow-node-event-message-start.svg?react';

import FlowNodeEventMessageIntermediateThrow from 'modules/components/Icon/flow-node-event-message-throw.svg?react';
import FlowNodeEventMessageBoundaryNonInterrupting from 'modules/components/Icon/flow-node-event-message-non-interrupting.svg?react';
import FlowNodeEventMessageBoundaryInterrupting from 'modules/components/Icon/flow-node-event-message-interrupting.svg?react';
import FlowNodeEventMessageEnd from 'modules/components/Icon/flow-node-event-message-end.svg?react';

import FlowNodeEventTimerStart from 'modules/components/Icon/flow-node-event-timer-start.svg?react';
import FlowNodeEventTimerBoundaryInterrupting from 'modules/components/Icon/flow-node-event-timer-interrupting.svg?react';
import FlowNodeEventTimerBoundaryNonInerrupting from 'modules/components/Icon/flow-node-event-timer-non-interrupting.svg?react';

import FlowNodeEventErrorStart from 'modules/components/Icon/flow-node-event-error-start.svg?react';
import FlowNodeEventErrorBoundary from 'modules/components/Icon/flow-node-event-error-boundary.svg?react';
import FlowNodeEventErrorEnd from 'modules/components/Icon/flow-node-event-error-end.svg?react';

import FlowNodeEventSubprocess from 'modules/components/Icon/flow-node-subprocess-event.svg?react';

import FlowNodeEventTerminateEnd from 'modules/components/Icon/flow-node-event-terminate-end.svg?react';
import FlowNodeEscalationEndEvent from 'modules/components/Icon/flow-node-escalation-end-event.svg?react';
import FlowNodeEscalationBoundaryEvent from 'modules/components/Icon/flow-node-escalation-boundary-event.svg?react';
import FlowNodeEscalationBoundaryNonInterruptingEvent from 'modules/components/Icon/flow-node-escalation-boundary-non-interrupting-event.svg?react';
import FlowNodeEscalationIntermediateThrowEvent from 'modules/components/Icon/flow-node-escalation-intermediate-throw-event.svg?react';
import FlowNodeEscalationNonInterruptingStartEvent from 'modules/components/Icon/flow-node-escalation-non-interrupting-start-event.svg?react';
import FlowNodeEscalationStartEvent from 'modules/components/Icon/flow-node-escalation-start-event.svg?react';

import FlowNodeLinkEventIntermediateCatch from 'modules/components/Icon/flow-node-link-event-intermediate-catch.svg?react';
import FlowNodeLinkEventIntermediateThrow from 'modules/components/Icon/flow-node-link-event-intermediate-throw.svg?react';
import FlowNodeEventSignalStart from 'modules/components/Icon/flow-node-event-signal-start.svg?react';
import FlowNodeEventSignalEnd from 'modules/components/Icon/flow-node-event-signal-end.svg?react';
import FlowNodeEventSignalIntermediateThrow from 'modules/components/Icon/flow-node-event-signal-intermediate-throw.svg?react';
import FlowNodeEventSignalIntermediateCatch from 'modules/components/Icon/flow-node-event-signal-intermediate-catch.svg?react';
import FlowNodeEventSignalInterruptingBoundary from 'modules/components/Icon/flow-node-event-signal-interrupting-boundary.svg?react';
import FlowNodeEventSignalNonInterruptingBoundary from 'modules/components/Icon/flow-node-event-signal-non-interrupting-boundary.svg?react';
import FlowNodeEventSignalNonInterruptingStart from 'modules/components/Icon/flow-node-event-signal-non-interrupting-start.svg?react';

import FlowNodeEventCompensationStart from 'modules/components/Icon/flow-node-compensation-start-event.svg?react';
import FlowNodeEventCompensationEnd from 'modules/components/Icon/flow-node-compensation-end-event.svg?react';
import FlowNodeEventCompensationIntermediateThrow from 'modules/components/Icon/flow-node-compensation-intermediate-event-throw.svg?react';
import FlowNodeEventCompensationBoundary from 'modules/components/Icon/flow-node-compensation-boundary-event.svg?react';

const getSVGComponent = (
  businessObject: BusinessObject | undefined,
  flowNodeInstanceType: string,
) => {
  if (businessObject === undefined) {
    return FlowNodeTask;
  }
  if (flowNodeInstanceType === 'MULTI_INSTANCE_BODY') {
    switch (getMultiInstanceType(businessObject)) {
      case 'parallel':
        return FlowNodeTaskParallel;
      case 'sequential':
        return FlowNodeTaskMulti;
    }
  } else if (flowNodeInstanceType === 'AD_HOC_SUB_PROCESS_INNER_INSTANCE') {
    return FlowNodeTaskSubProcessAdhocInnerInstance;
  } else if (businessObject === undefined) {
    return FlowNodeTask;
  }

  switch (getEventType(businessObject)) {
    case 'bpmn:ErrorEventDefinition':
      switch (businessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          return FlowNodeEventErrorStart;
        case 'bpmn:EndEvent':
          return FlowNodeEventErrorEnd;
        case 'bpmn:BoundaryEvent':
          return FlowNodeEventErrorBoundary;
      }
    case 'bpmn:MessageEventDefinition':
      switch (businessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          return FlowNodeEventMessageStart;
        case 'bpmn:EndEvent':
          return FlowNodeEventMessageEnd;
        case 'bpmn:IntermediateCatchEvent':
          // uses the same style as boundary interrupting
          return FlowNodeEventMessageBoundaryInterrupting;
        case 'bpmn:IntermediateThrowEvent':
          return FlowNodeEventMessageIntermediateThrow;
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(businessObject)) {
            default:
            case 'interrupting':
              return FlowNodeEventMessageBoundaryInterrupting;

            case 'non-interrupting':
              return FlowNodeEventMessageBoundaryNonInterrupting;
          }
      }
    case 'bpmn:TimerEventDefinition':
      switch (businessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          return FlowNodeEventTimerStart;
        case 'bpmn:IntermediateCatchEvent':
          return FlowNodeEventTimerBoundaryInterrupting;
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(businessObject)) {
            default:
            case 'interrupting':
              return FlowNodeEventTimerBoundaryInterrupting;
            case 'non-interrupting':
              return FlowNodeEventTimerBoundaryNonInerrupting;
          }
      }
    case 'bpmn:TerminateEventDefinition':
      return FlowNodeEventTerminateEnd;

    case 'bpmn:LinkEventDefinition':
      switch (businessObject.$type) {
        default:
        case 'bpmn:IntermediateCatchEvent':
          return FlowNodeLinkEventIntermediateCatch;
        case 'bpmn:IntermediateThrowEvent':
          return FlowNodeLinkEventIntermediateThrow;
      }

    case 'bpmn:EscalationEventDefinition':
      switch (businessObject.$type) {
        default:
        case 'bpmn:EndEvent':
          return FlowNodeEscalationEndEvent;
        case 'bpmn:StartEvent':
          switch (isInterruptingEvent(businessObject)) {
            default:
            case true:
              return FlowNodeEscalationStartEvent;
            case false:
              return FlowNodeEscalationNonInterruptingStartEvent;
          }
        case 'bpmn:IntermediateThrowEvent':
          return FlowNodeEscalationIntermediateThrowEvent;
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(businessObject)) {
            default:
            case 'interrupting':
              return FlowNodeEscalationBoundaryEvent;
            case 'non-interrupting':
              return FlowNodeEscalationBoundaryNonInterruptingEvent;
          }
      }

    case 'bpmn:SignalEventDefinition':
      switch (businessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          switch (isInterruptingEvent(businessObject)) {
            default:
            case true:
              return FlowNodeEventSignalStart;
            case false:
              return FlowNodeEventSignalNonInterruptingStart;
          }
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(businessObject)) {
            default:
            case 'interrupting':
              return FlowNodeEventSignalInterruptingBoundary;
            case 'non-interrupting':
              return FlowNodeEventSignalNonInterruptingBoundary;
          }
        case 'bpmn:IntermediateThrowEvent':
          return FlowNodeEventSignalIntermediateThrow;
        case 'bpmn:IntermediateCatchEvent':
          return FlowNodeEventSignalIntermediateCatch;
        case 'bpmn:EndEvent':
          return FlowNodeEventSignalEnd;
      }

    case 'bpmn:CompensateEventDefinition':
      switch (businessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          return FlowNodeEventCompensationStart;
        case 'bpmn:BoundaryEvent':
          return FlowNodeEventCompensationBoundary;
        case 'bpmn:IntermediateThrowEvent':
          return FlowNodeEventCompensationIntermediateThrow;
        case 'bpmn:EndEvent':
          return FlowNodeEventCompensationEnd;
      }
  }

  switch (businessObject.$type) {
    case 'bpmn:StartEvent':
      return FlowNodeEventStart;
    case 'bpmn:EndEvent':
      return FlowNodeEventEnd;
    case 'bpmn:ServiceTask':
      return FlowNodeTaskService;
    case 'bpmn:UserTask':
      return FlowNodeTaskUser;
    case 'bpmn:BusinessRuleTask':
      return FlowNodeTaskBusinessRule;
    case 'bpmn:ScriptTask':
      return FlowNodeTaskScript;
    case 'bpmn:ReceiveTask':
      return FlowNodeTaskReceive;
    case 'bpmn:SendTask':
      return FlowNodeTaskSend;
    case 'bpmn:ManualTask':
      return FlowNodeTaskManual;
    case 'bpmn:CallActivity':
      return FlowNodeCallActivity;
    case 'bpmn:EventBasedGateway':
      return FlowNodeGatewayEventBased;
    case 'bpmn:ParallelGateway':
      return FlowNodeGatewayParallel;
    case 'bpmn:ExclusiveGateway':
      return FlowNodeGatewayExclusive;
    case 'bpmn:InclusiveGateway':
      return FlowNodeGatewayInclusive;
    case 'bpmn:Process':
      return FlowNodeProcess;
    case 'bpmn:IntermediateThrowEvent':
      return FlowNodeEventIntermediateThrow;
    case 'bpmn:SubProcess':
      return isEventSubProcess({businessObject})
        ? FlowNodeEventSubprocess
        : FlowNodeTaskSubProcess;
    case 'bpmn:AdHocSubProcess':
      return FlowNodeTaskSubProcessAdhoc;
  }

  return FlowNodeTask;
};

type Props = {
  flowNodeInstanceType: FlowNodeInstance['type'];
  diagramBusinessObject: BusinessObject | undefined;
  className?: string;
  hasLeftMargin?: boolean;
};

const FlowNodeIcon: React.FC<Props> = ({
  flowNodeInstanceType,
  diagramBusinessObject,
  className,
  hasLeftMargin = false,
}) => {
  const SVGComponent = getSVGComponent(
    diagramBusinessObject,
    flowNodeInstanceType,
  );

  return (
    <SVGIcon
      SVGComponent={SVGComponent}
      $isGateway={
        diagramBusinessObject !== undefined &&
        ['bpmn:ParallelGateway', 'bpmn:ExclusiveGateway'].includes(
          diagramBusinessObject.$type,
        )
      }
      className={className}
      $hasLeftMargin={hasLeftMargin}
      data-testid="flow-node-icon"
    />
  );
};

export {FlowNodeIcon};
