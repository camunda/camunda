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
import {isInterruptingEvent} from 'modules/bpmn-js/utils/isInterruptingEvent';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';

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
import FlowNodeEventTimerBoundaryNonInterrupting from 'modules/components/Icon/flow-node-event-timer-non-interrupting.svg?react';

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

type Props = {
  elementInstanceType: ElementInstance['type'];
  diagramBusinessObject: BusinessObject | undefined;
  className?: string;
};

const ElementInstanceIcon: React.FC<Props> = ({
  elementInstanceType,
  diagramBusinessObject,
  className,
  ...rest
}) => {
  const isGateway = [
    'PARALLEL_GATEWAY',
    'EXCLUSIVE_GATEWAY',
    'INCLUSIVE_GATEWAY',
    'EVENT_BASED_GATEWAY',
  ].includes(elementInstanceType);
  const svgProps = {
    style: isGateway
      ? {
          position: 'relative' as const,
          top: 3,
          right: 2,
        }
      : undefined,
    className,
    'data-testid': 'element-instance-icon',
    ...rest,
  };

  if (elementInstanceType === 'AD_HOC_SUB_PROCESS_INNER_INSTANCE') {
    return <FlowNodeTaskSubProcessAdhocInnerInstance {...svgProps} />;
  }

  if (elementInstanceType === 'AD_HOC_SUB_PROCESS') {
    return <FlowNodeTaskSubProcessAdhoc {...svgProps} />;
  }

  if (elementInstanceType === 'EXCLUSIVE_GATEWAY') {
    return <FlowNodeGatewayExclusive {...svgProps} />;
  }

  if (elementInstanceType === 'INCLUSIVE_GATEWAY') {
    return <FlowNodeGatewayInclusive {...svgProps} />;
  }

  if (elementInstanceType === 'PARALLEL_GATEWAY') {
    return <FlowNodeGatewayParallel {...svgProps} />;
  }

  if (elementInstanceType === 'EVENT_BASED_GATEWAY') {
    return <FlowNodeGatewayEventBased {...svgProps} />;
  }

  if (elementInstanceType === 'SERVICE_TASK') {
    return <FlowNodeTaskService {...svgProps} />;
  }

  if (elementInstanceType === 'USER_TASK') {
    return <FlowNodeTaskUser {...svgProps} />;
  }

  if (elementInstanceType === 'BUSINESS_RULE_TASK') {
    return <FlowNodeTaskBusinessRule {...svgProps} />;
  }

  if (elementInstanceType === 'SCRIPT_TASK') {
    return <FlowNodeTaskScript {...svgProps} />;
  }

  if (elementInstanceType === 'RECEIVE_TASK') {
    return <FlowNodeTaskReceive {...svgProps} />;
  }

  if (elementInstanceType === 'SEND_TASK') {
    return <FlowNodeTaskSend {...svgProps} />;
  }

  if (elementInstanceType === 'MANUAL_TASK') {
    return <FlowNodeTaskManual {...svgProps} />;
  }

  if (elementInstanceType === 'CALL_ACTIVITY') {
    return <FlowNodeCallActivity {...svgProps} />;
  }

  if (elementInstanceType === 'PROCESS') {
    return <FlowNodeProcess {...svgProps} />;
  }

  if (elementInstanceType === 'EVENT_SUB_PROCESS') {
    return <FlowNodeEventSubprocess {...svgProps} />;
  }

  if (elementInstanceType === 'SUB_PROCESS') {
    return <FlowNodeTaskSubProcess {...svgProps} />;
  }

  if (diagramBusinessObject === undefined) {
    return <FlowNodeTask {...svgProps} />;
  }

  if (elementInstanceType === 'MULTI_INSTANCE_BODY') {
    const multiInstanceType = getMultiInstanceType(diagramBusinessObject);

    if (multiInstanceType === 'parallel') {
      return <FlowNodeTaskParallel {...svgProps} />;
    }

    if (multiInstanceType === 'sequential') {
      return <FlowNodeTaskMulti {...svgProps} />;
    }
  }

  switch (getEventType(diagramBusinessObject)) {
    case 'bpmn:ErrorEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          return <FlowNodeEventErrorStart {...svgProps} />;
        case 'bpmn:EndEvent':
          return <FlowNodeEventErrorEnd {...svgProps} />;
        case 'bpmn:BoundaryEvent':
          return <FlowNodeEventErrorBoundary {...svgProps} />;
      }
    case 'bpmn:MessageEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          return <FlowNodeEventMessageStart {...svgProps} />;
        case 'bpmn:EndEvent':
          return <FlowNodeEventMessageEnd {...svgProps} />;
        case 'bpmn:IntermediateCatchEvent':
          // uses the same style as boundary interrupting
          return <FlowNodeEventMessageBoundaryInterrupting {...svgProps} />;
        case 'bpmn:IntermediateThrowEvent':
          return <FlowNodeEventMessageIntermediateThrow {...svgProps} />;
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(diagramBusinessObject)) {
            default:
            case 'interrupting':
              return <FlowNodeEventMessageBoundaryInterrupting {...svgProps} />;

            case 'non-interrupting':
              return (
                <FlowNodeEventMessageBoundaryNonInterrupting {...svgProps} />
              );
          }
      }
    case 'bpmn:TimerEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          return <FlowNodeEventTimerStart {...svgProps} />;
        case 'bpmn:IntermediateCatchEvent':
          return <FlowNodeEventTimerBoundaryInterrupting {...svgProps} />;
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(diagramBusinessObject)) {
            default:
            case 'interrupting':
              return <FlowNodeEventTimerBoundaryInterrupting {...svgProps} />;
            case 'non-interrupting':
              return (
                <FlowNodeEventTimerBoundaryNonInterrupting {...svgProps} />
              );
          }
      }
    case 'bpmn:TerminateEventDefinition':
      return <FlowNodeEventTerminateEnd {...svgProps} />;

    case 'bpmn:LinkEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:IntermediateCatchEvent':
          return <FlowNodeLinkEventIntermediateCatch {...svgProps} />;
        case 'bpmn:IntermediateThrowEvent':
          return <FlowNodeLinkEventIntermediateThrow {...svgProps} />;
      }

    case 'bpmn:EscalationEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:EndEvent':
          return <FlowNodeEscalationEndEvent {...svgProps} />;
        case 'bpmn:StartEvent':
          switch (isInterruptingEvent(diagramBusinessObject)) {
            default:
            case true:
              return <FlowNodeEscalationStartEvent {...svgProps} />;
            case false:
              return (
                <FlowNodeEscalationNonInterruptingStartEvent {...svgProps} />
              );
          }
        case 'bpmn:IntermediateThrowEvent':
          return <FlowNodeEscalationIntermediateThrowEvent {...svgProps} />;
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(diagramBusinessObject)) {
            default:
            case 'interrupting':
              return <FlowNodeEscalationBoundaryEvent {...svgProps} />;
            case 'non-interrupting':
              return (
                <FlowNodeEscalationBoundaryNonInterruptingEvent {...svgProps} />
              );
          }
      }

    case 'bpmn:SignalEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          switch (isInterruptingEvent(diagramBusinessObject)) {
            default:
            case true:
              return <FlowNodeEventSignalStart {...svgProps} />;
            case false:
              return <FlowNodeEventSignalNonInterruptingStart {...svgProps} />;
          }
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(diagramBusinessObject)) {
            default:
            case 'interrupting':
              return <FlowNodeEventSignalInterruptingBoundary {...svgProps} />;
            case 'non-interrupting':
              return (
                <FlowNodeEventSignalNonInterruptingBoundary {...svgProps} />
              );
          }
        case 'bpmn:IntermediateThrowEvent':
          return <FlowNodeEventSignalIntermediateThrow {...svgProps} />;
        case 'bpmn:IntermediateCatchEvent':
          return <FlowNodeEventSignalIntermediateCatch {...svgProps} />;
        case 'bpmn:EndEvent':
          return <FlowNodeEventSignalEnd {...svgProps} />;
      }

    case 'bpmn:CompensateEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          return <FlowNodeEventCompensationStart {...svgProps} />;
        case 'bpmn:BoundaryEvent':
          return <FlowNodeEventCompensationBoundary {...svgProps} />;
        case 'bpmn:IntermediateThrowEvent':
          return <FlowNodeEventCompensationIntermediateThrow {...svgProps} />;
        case 'bpmn:EndEvent':
          return <FlowNodeEventCompensationEnd {...svgProps} />;
      }
  }

  if (elementInstanceType === 'START_EVENT') {
    return <FlowNodeEventStart {...svgProps} />;
  }

  if (elementInstanceType === 'END_EVENT') {
    return <FlowNodeEventEnd {...svgProps} />;
  }

  if (elementInstanceType === 'INTERMEDIATE_THROW_EVENT') {
    return <FlowNodeEventIntermediateThrow {...svgProps} />;
  }

  return <FlowNodeTask {...svgProps} />;
};

export {ElementInstanceIcon};
