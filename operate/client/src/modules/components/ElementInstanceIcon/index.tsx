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

import ElementProcess from 'modules/components/Icon/element-process-root.svg?react';

/**
 * Gateway Imports
 */
import ElementGatewayInclusive from 'modules/components/Icon/element-gateway-inclusive-or.svg?react';
import ElementGatewayExclusive from 'modules/components/Icon/element-gateway-exclusive.svg?react';
import ElementGatewayParallel from 'modules/components/Icon/element-gateway-parallel.svg?react';
import ElementGatewayEventBased from 'modules/components/Icon/element-gateway-event-based.svg?react';

/**
 * Task Imports
 */

import ElementTask from 'modules/components/Icon/element-task-undefined.svg?react';
import ElementTaskService from 'modules/components/Icon/element-task-service.svg?react';
import ElementTaskReceive from 'modules/components/Icon/element-task-receive.svg?react';
import ElementTaskSend from 'modules/components/Icon/element-task-send.svg?react';
import ElementTaskSubProcess from 'modules/components/Icon/element-subprocess-embedded.svg?react';
import ElementTaskSubProcessAdhoc from 'modules/components/Icon/element-subprocess-adhoc.svg?react';
import ElementTaskSubProcessAdhocInnerInstance from 'modules/components/Icon/element-subprocess-adhoc-inner-instance.svg?react';
import ElementTaskMultiParallel from 'modules/components/Icon/element-multi-instance-parallel.svg?react';
import ElementTaskSequential from 'modules/components/Icon/element-multi-instance-sequential.svg?react';
import ElementCallActivity from 'modules/components/Icon/element-call-activity.svg?react';
import ElementTaskUser from 'modules/components/Icon/element-task-user.svg?react';
import ElementTaskBusinessRule from 'modules/components/Icon/element-task-business-rule.svg?react';
import ElementTaskScript from 'modules/components/Icon/element-task-script.svg?react';
import ElementTaskManual from 'modules/components/Icon/element-task-manual.svg?react';

/**
 * Event Imports
 */

import ElementEventStart from 'modules/components/Icon/element-event-start.svg?react';
import ElementEventEnd from 'modules/components/Icon/element-event-end.svg?react';
import ElementEventIntermediateThrow from 'modules/components/Icon/element-event-intermediate-none.svg?react';

import ElementEventMessageStart from 'modules/components/Icon/element-event-message-start.svg?react';

import ElementEventMessageIntermediateThrow from 'modules/components/Icon/element-event-message-throw.svg?react';
import ElementEventMessageBoundaryNonInterrupting from 'modules/components/Icon/element-event-message-non-interrupting.svg?react';
import ElementEventMessageBoundaryInterrupting from 'modules/components/Icon/element-event-message-interrupting.svg?react';
import ElementEventMessageEnd from 'modules/components/Icon/element-event-message-end.svg?react';

import ElementEventTimerStart from 'modules/components/Icon/element-event-timer-start.svg?react';
import ElementEventTimerBoundaryInterrupting from 'modules/components/Icon/element-event-timer-interrupting.svg?react';
import ElementEventTimerBoundaryNonInterrupting from 'modules/components/Icon/element-event-timer-non-interrupting.svg?react';

import ElementEventErrorStart from 'modules/components/Icon/element-event-error-start.svg?react';
import ElementEventErrorBoundary from 'modules/components/Icon/element-event-error-boundary.svg?react';
import ElementEventErrorEnd from 'modules/components/Icon/element-event-error-end.svg?react';

import ElementEventSubprocess from 'modules/components/Icon/element-subprocess-event.svg?react';

import ElementEventTerminateEnd from 'modules/components/Icon/element-event-terminate-end.svg?react';
import ElementEscalationEndEvent from 'modules/components/Icon/element-escalation-end-event.svg?react';
import ElementEscalationBoundaryEvent from 'modules/components/Icon/element-escalation-boundary-event.svg?react';
import ElementEscalationBoundaryNonInterruptingEvent from 'modules/components/Icon/element-escalation-boundary-non-interrupting-event.svg?react';
import ElementEscalationIntermediateThrowEvent from 'modules/components/Icon/element-escalation-intermediate-throw-event.svg?react';
import ElementEscalationNonInterruptingStartEvent from 'modules/components/Icon/element-escalation-non-interrupting-start-event.svg?react';
import ElementEscalationStartEvent from 'modules/components/Icon/element-escalation-start-event.svg?react';

import ElementLinkEventIntermediateCatch from 'modules/components/Icon/element-link-event-intermediate-catch.svg?react';
import ElementLinkEventIntermediateThrow from 'modules/components/Icon/element-link-event-intermediate-throw.svg?react';
import ElementEventSignalStart from 'modules/components/Icon/element-event-signal-start.svg?react';
import ElementEventSignalEnd from 'modules/components/Icon/element-event-signal-end.svg?react';
import ElementEventSignalIntermediateThrow from 'modules/components/Icon/element-event-signal-intermediate-throw.svg?react';
import ElementEventSignalIntermediateCatch from 'modules/components/Icon/element-event-signal-intermediate-catch.svg?react';
import ElementEventSignalInterruptingBoundary from 'modules/components/Icon/element-event-signal-interrupting-boundary.svg?react';
import ElementEventSignalNonInterruptingBoundary from 'modules/components/Icon/element-event-signal-non-interrupting-boundary.svg?react';
import ElementEventSignalNonInterruptingStart from 'modules/components/Icon/element-event-signal-non-interrupting-start.svg?react';

import ElementEventCompensationStart from 'modules/components/Icon/element-compensation-start-event.svg?react';
import ElementEventCompensationEnd from 'modules/components/Icon/element-compensation-end-event.svg?react';
import ElementEventCompensationIntermediateThrow from 'modules/components/Icon/element-compensation-intermediate-event-throw.svg?react';
import ElementEventCompensationBoundary from 'modules/components/Icon/element-compensation-boundary-event.svg?react';

import ElementEventConditionalStart from 'modules/components/Icon/element-conditional-start-event.svg?react';
import ElementEventConditionalIntermediateCatch from 'modules/components/Icon/element-conditional-intermediate-catch-event.svg?react';
import ElementEventConditionalNonInterrupting from 'modules/components/Icon/element-conditional-intermediate-catch-non-interrupting-event.svg?react';
import ElementEventConditionalNonInterruptingStart from 'modules/components/Icon/element-conditional-intermediate-catch-non-interrupting-start-event.svg?react';

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
      <ElementProcess
        className={className}
        data-testid="element-instance-icon"
        {...rest}
      />
    );
  }

  if (diagramBusinessObject === undefined) {
    return (
      <ElementTask
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
    return <ElementTaskSubProcessAdhocInnerInstance {...svgProps} />;
  }

  if (isAdHocSubProcess(diagramBusinessObject)) {
    return <ElementTaskSubProcessAdhoc {...svgProps} />;
  }

  if (isEventSubProcess({businessObject: diagramBusinessObject})) {
    return <ElementEventSubprocess {...svgProps} />;
  }

  switch (businessObjectTypeName) {
    case 'bpmn:ExclusiveGateway':
      return <ElementGatewayExclusive {...svgProps} />;
    case 'bpmn:InclusiveGateway':
      return <ElementGatewayInclusive {...svgProps} />;
    case 'bpmn:ParallelGateway':
      return <ElementGatewayParallel {...svgProps} />;
    case 'bpmn:EventBasedGateway':
      return <ElementGatewayEventBased {...svgProps} />;
    case 'bpmn:ServiceTask':
      return <ElementTaskService {...svgProps} />;
    case 'bpmn:UserTask':
      return <ElementTaskUser {...svgProps} />;
    case 'bpmn:BusinessRuleTask':
      return <ElementTaskBusinessRule {...svgProps} />;
    case 'bpmn:ScriptTask':
      return <ElementTaskScript {...svgProps} />;
    case 'bpmn:ReceiveTask':
      return <ElementTaskReceive {...svgProps} />;
    case 'bpmn:SendTask':
      return <ElementTaskSend {...svgProps} />;
    case 'bpmn:ManualTask':
      return <ElementTaskManual {...svgProps} />;
    case 'bpmn:CallActivity':
      return <ElementCallActivity {...svgProps} />;
    case 'bpmn:Process':
      return <ElementProcess {...svgProps} />;
    case 'bpmn:SubProcess':
      return <ElementTaskSubProcess {...svgProps} />;
  }

  if (isMultiInstance(diagramBusinessObject)) {
    const multiInstanceType = getMultiInstanceType(diagramBusinessObject);

    if (multiInstanceType === 'parallel') {
      return <ElementTaskSequential {...svgProps} />;
    }

    if (multiInstanceType === 'sequential') {
      return <ElementTaskMultiParallel {...svgProps} />;
    }
  }

  switch (getEventType(diagramBusinessObject)) {
    case 'bpmn:ErrorEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          return <ElementEventErrorStart {...svgProps} />;
        case 'bpmn:EndEvent':
          return <ElementEventErrorEnd {...svgProps} />;
        case 'bpmn:BoundaryEvent':
          return <ElementEventErrorBoundary {...svgProps} />;
      }
    case 'bpmn:MessageEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          return <ElementEventMessageStart {...svgProps} />;
        case 'bpmn:EndEvent':
          return <ElementEventMessageEnd {...svgProps} />;
        case 'bpmn:IntermediateCatchEvent':
          // uses the same style as boundary interrupting
          return <ElementEventMessageBoundaryInterrupting {...svgProps} />;
        case 'bpmn:IntermediateThrowEvent':
          return <ElementEventMessageIntermediateThrow {...svgProps} />;
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(diagramBusinessObject)) {
            default:
            case 'interrupting':
              return <ElementEventMessageBoundaryInterrupting {...svgProps} />;

            case 'non-interrupting':
              return (
                <ElementEventMessageBoundaryNonInterrupting {...svgProps} />
              );
          }
      }
    case 'bpmn:TimerEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          return <ElementEventTimerStart {...svgProps} />;
        case 'bpmn:IntermediateCatchEvent':
          return <ElementEventTimerBoundaryInterrupting {...svgProps} />;
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(diagramBusinessObject)) {
            default:
            case 'interrupting':
              return <ElementEventTimerBoundaryInterrupting {...svgProps} />;
            case 'non-interrupting':
              return <ElementEventTimerBoundaryNonInterrupting {...svgProps} />;
          }
      }
    case 'bpmn:TerminateEventDefinition':
      return <ElementEventTerminateEnd {...svgProps} />;

    case 'bpmn:LinkEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:IntermediateCatchEvent':
          return <ElementLinkEventIntermediateCatch {...svgProps} />;
        case 'bpmn:IntermediateThrowEvent':
          return <ElementLinkEventIntermediateThrow {...svgProps} />;
      }

    case 'bpmn:EscalationEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:EndEvent':
          return <ElementEscalationEndEvent {...svgProps} />;
        case 'bpmn:StartEvent':
          switch (isInterruptingEvent(diagramBusinessObject)) {
            default:
            case true:
              return <ElementEscalationStartEvent {...svgProps} />;
            case false:
              return (
                <ElementEscalationNonInterruptingStartEvent {...svgProps} />
              );
          }
        case 'bpmn:IntermediateThrowEvent':
          return <ElementEscalationIntermediateThrowEvent {...svgProps} />;
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(diagramBusinessObject)) {
            default:
            case 'interrupting':
              return <ElementEscalationBoundaryEvent {...svgProps} />;
            case 'non-interrupting':
              return (
                <ElementEscalationBoundaryNonInterruptingEvent {...svgProps} />
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
              return <ElementEventSignalStart {...svgProps} />;
            case false:
              return <ElementEventSignalNonInterruptingStart {...svgProps} />;
          }
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(diagramBusinessObject)) {
            default:
            case 'interrupting':
              return <ElementEventSignalInterruptingBoundary {...svgProps} />;
            case 'non-interrupting':
              return (
                <ElementEventSignalNonInterruptingBoundary {...svgProps} />
              );
          }
        case 'bpmn:IntermediateThrowEvent':
          return <ElementEventSignalIntermediateThrow {...svgProps} />;
        case 'bpmn:IntermediateCatchEvent':
          return <ElementEventSignalIntermediateCatch {...svgProps} />;
        case 'bpmn:EndEvent':
          return <ElementEventSignalEnd {...svgProps} />;
      }

    case 'bpmn:CompensateEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          return <ElementEventCompensationStart {...svgProps} />;
        case 'bpmn:BoundaryEvent':
          return <ElementEventCompensationBoundary {...svgProps} />;
        case 'bpmn:IntermediateThrowEvent':
          return <ElementEventCompensationIntermediateThrow {...svgProps} />;
        case 'bpmn:EndEvent':
          return <ElementEventCompensationEnd {...svgProps} />;
      }

    case 'bpmn:ConditionalEventDefinition':
      switch (diagramBusinessObject.$type) {
        default:
        case 'bpmn:StartEvent':
          switch (isInterruptingEvent(diagramBusinessObject)) {
            default:
            case true:
              return <ElementEventConditionalStart {...svgProps} />;
            case false:
              return (
                <ElementEventConditionalNonInterruptingStart {...svgProps} />
              );
          }
        case 'bpmn:IntermediateCatchEvent':
          return <ElementEventConditionalIntermediateCatch {...svgProps} />;
        case 'bpmn:BoundaryEvent':
          switch (getBoundaryEventType(diagramBusinessObject)) {
            default:
            case 'interrupting':
              return <ElementEventConditionalIntermediateCatch {...svgProps} />;
            case 'non-interrupting':
              return <ElementEventConditionalNonInterrupting {...svgProps} />;
          }
      }
  }

  switch (businessObjectTypeName) {
    case 'bpmn:StartEvent':
      return <ElementEventStart {...svgProps} />;
    case 'bpmn:EndEvent':
      return <ElementEventEnd {...svgProps} />;
    case 'bpmn:IntermediateThrowEvent':
      return <ElementEventIntermediateThrow {...svgProps} />;
  }

  return <ElementTask {...svgProps} />;
};

export {ElementInstanceIcon};
