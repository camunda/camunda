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
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {isAdHocSubProcessInnerInstance} from 'modules/bpmn-js/utils/isAdHocSubProcessInnerInstance';
import {isAdHocSubProcess} from 'modules/bpmn-js/utils/isAdHocSubProcess';
import {isEventSubProcess} from 'modules/bpmn-js/utils/isEventSubProcess';

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

import FlowNodeEventConditionalStart from 'modules/components/Icon/flow-node-conditional-start-event.svg?react';
import FlowNodeEventConditionalIntermediateCatch from 'modules/components/Icon/flow-node-conditional-intermediate-catch-event.svg?react';
import FlowNodeEventConditionalNonInterrupting from 'modules/components/Icon/flow-node-conditional-intermediate-catch-non-interrupting-event.svg?react';
import FlowNodeEventConditionalNonInterruptingStart from 'modules/components/Icon/flow-node-conditional-intermediate-catch-non-interrupting-start-event.svg?react';

type Props = {
  diagramBusinessObject: BusinessObject | undefined;
  isRootProcess?: boolean;
  className?: string;
};

const ElementInstanceIcon: React.FC<Props> = ({
  diagramBusinessObject,
  className,
  isRootProcess = false,
  ...rest
}) => {
  if (isRootProcess) {
    return (
      <FlowNodeProcess
        className={className}
        data-testid="element-instance-icon"
        {...rest}
      />
    );
  }

  if (diagramBusinessObject === undefined) {
    return (
      <FlowNodeTask
        className={className}
        data-testid="element-instance-icon"
        {...rest}
      />
    );
  }

  const businessObjectTypeName = diagramBusinessObject.$type;
  const isGateway = [
    'bpmn:ParallelGateway',
    'bpmn:ExclusiveGateway',
    'bpmn:InclusiveGateway',
    'bpmn:EventBasedGateway',
  ].includes(businessObjectTypeName);
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

  if (isAdHocSubProcessInnerInstance(diagramBusinessObject)) {
    return <FlowNodeTaskSubProcessAdhocInnerInstance {...svgProps} />;
  }

  if (isAdHocSubProcess(diagramBusinessObject)) {
    return <FlowNodeTaskSubProcessAdhoc {...svgProps} />;
  }

  if (isEventSubProcess({businessObject: diagramBusinessObject})) {
    return <FlowNodeEventSubprocess {...svgProps} />;
  }

  switch (businessObjectTypeName) {
    case 'bpmn:ExclusiveGateway':
      return <FlowNodeGatewayExclusive {...svgProps} />;
    case 'bpmn:InclusiveGateway':
      return <FlowNodeGatewayInclusive {...svgProps} />;
    case 'bpmn:ParallelGateway':
      return <FlowNodeGatewayParallel {...svgProps} />;
    case 'bpmn:EventBasedGateway':
      return <FlowNodeGatewayEventBased {...svgProps} />;
    case 'bpmn:ServiceTask':
      return <FlowNodeTaskService {...svgProps} />;
    case 'bpmn:UserTask':
      return <FlowNodeTaskUser {...svgProps} />;
    case 'bpmn:BusinessRuleTask':
      return <FlowNodeTaskBusinessRule {...svgProps} />;
    case 'bpmn:ScriptTask':
      return <FlowNodeTaskScript {...svgProps} />;
    case 'bpmn:ReceiveTask':
      return <FlowNodeTaskReceive {...svgProps} />;
    case 'bpmn:SendTask':
      return <FlowNodeTaskSend {...svgProps} />;
    case 'bpmn:ManualTask':
      return <FlowNodeTaskManual {...svgProps} />;
    case 'bpmn:CallActivity':
      return <FlowNodeCallActivity {...svgProps} />;
    case 'bpmn:Process':
      return <FlowNodeProcess {...svgProps} />;
    case 'bpmn:SubProcess':
      return <FlowNodeTaskSubProcess {...svgProps} />;
  }

  if (diagramBusinessObject === undefined) {
    return <FlowNodeTask {...svgProps} />;
  }

  if (isMultiInstance(diagramBusinessObject)) {
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

    case 'bpmn:ConditionalEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          switch (isInterruptingEvent(diagramBusinessObject)) {
            default:
            case true:
              return <FlowNodeEventConditionalStart {...svgProps} />;
            case false:
              return (
                <FlowNodeEventConditionalNonInterruptingStart {...svgProps} />
              );
          }
        case 'bpmn:IntermediateCatchEvent':
          return <FlowNodeEventConditionalIntermediateCatch {...svgProps} />;
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(diagramBusinessObject)) {
            default:
            case 'interrupting':
              return <FlowNodeEventConditionalIntermediateCatch {...svgProps} />;
            case 'non-interrupting':
              return <FlowNodeEventConditionalNonInterrupting {...svgProps} />;
          }
      }
  }

  switch (businessObjectTypeName) {
    case 'bpmn:StartEvent':
      return <FlowNodeEventStart {...svgProps} />;
    case 'bpmn:EndEvent':
      return <FlowNodeEventEnd {...svgProps} />;
    case 'bpmn:IntermediateThrowEvent':
      return <FlowNodeEventIntermediateThrow {...svgProps} />;
  }

  return <FlowNodeTask {...svgProps} />;
};

export {ElementInstanceIcon};
