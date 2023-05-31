/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {getBoundaryEventType} from 'modules/bpmn-js/utils/getBoundaryEventType';
import {getEventType} from 'modules/bpmn-js/utils/getEventType';
import {getMultiInstanceType} from 'modules/bpmn-js/utils/getMultiInstanceType';
import {isEventSubProcess} from 'modules/bpmn-js/utils/isEventSubProcess';
import {isInterruptingEvent} from 'modules/bpmn-js/utils/isInterruptingEvent';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';
import {SVGIcon} from './styled';

import {ReactComponent as FlowNodeProcess} from 'modules/components/Icon/flow-node-process-root.svg';

/**
 * Gateway Imports
 */
import {ReactComponent as FlowNodeGatewayInclusive} from 'modules/components/Icon/flow-node-gateway-inclusive-or.svg';
import {ReactComponent as FlowNodeGatewayExclusive} from 'modules/components/Icon/flow-node-gateway-exclusive.svg';
import {ReactComponent as FlowNodeGatewayParallel} from 'modules/components/Icon/flow-node-gateway-parallel.svg';
import {ReactComponent as FlowNodeGatewayEventBased} from 'modules/components/Icon/flow-node-gateway-event-based.svg';

/**
 * Task Imports
 */

import {ReactComponent as FlowNodeTask} from 'modules/components/Icon/flow-node-task-undefined.svg';
import {ReactComponent as FlowNodeTaskService} from 'modules/components/Icon/flow-node-task-service.svg';
import {ReactComponent as FlowNodeTaskReceive} from 'modules/components/Icon/flow-node-task-receive.svg';
import {ReactComponent as FlowNodeTaskSend} from 'modules/components/Icon/flow-node-task-send.svg';
import {ReactComponent as FlowNodeTaskSubProcess} from 'modules/components/Icon/flow-node-subprocess-embedded.svg';
import {ReactComponent as FlowNodeTaskMulti} from 'modules/components/Icon/flow-node-multi-instance-parallel.svg';
import {ReactComponent as FlowNodeTaskParallel} from 'modules/components/Icon/flow-node-multi-instance-sequential.svg';
import {ReactComponent as FlowNodeCallActivity} from 'modules/components/Icon/flow-node-call-activity.svg';
import {ReactComponent as FlowNodeTaskUser} from 'modules/components/Icon/flow-node-task-user.svg';
import {ReactComponent as FlowNodeTaskBusinessRule} from 'modules/components/Icon/flow-node-task-business-rule.svg';
import {ReactComponent as FlowNodeTaskScript} from 'modules/components/Icon/flow-node-task-script.svg';
import {ReactComponent as FlowNodeTaskManual} from 'modules/components/Icon/flow-node-task-manual.svg';

/**
 * Event Imports
 */

import {ReactComponent as FlowNodeEventStart} from 'modules/components/Icon/flow-node-event-start.svg';
import {ReactComponent as FlowNodeEventEnd} from 'modules/components/Icon/flow-node-event-end.svg';
import {ReactComponent as FlowNodeEventIntermediateThrow} from 'modules/components/Icon/flow-node-event-intermediate-none.svg';

import {ReactComponent as FlowNodeEventMessageStart} from 'modules/components/Icon/flow-node-event-message-start.svg';

import {ReactComponent as FlowNodeEventMessageIntermediateThrow} from 'modules/components/Icon/flow-node-event-message-throw.svg';
import {ReactComponent as FlowNodeEventMessageBoundaryNonInterrupting} from 'modules/components/Icon/flow-node-event-message-non-interrupting.svg';
import {ReactComponent as FlowNodeEventMessageBoundaryInterrupting} from 'modules/components/Icon/flow-node-event-message-interrupting.svg';
import {ReactComponent as FlowNodeEventMessageEnd} from 'modules/components/Icon/flow-node-event-message-end.svg';

import {ReactComponent as FlowNodeEventTimerStart} from 'modules/components/Icon/flow-node-event-timer-start.svg';
import {ReactComponent as FlowNodeEventTimerBoundaryInterrupting} from 'modules/components/Icon/flow-node-event-timer-interrupting.svg';
import {ReactComponent as FlowNodeEventTimerBoundaryNonInerrupting} from 'modules/components/Icon/flow-node-event-timer-non-interrupting.svg';

import {ReactComponent as FlowNodeEventErrorStart} from 'modules/components/Icon/flow-node-event-error-start.svg';
import {ReactComponent as FlowNodeEventErrorBoundary} from 'modules/components/Icon/flow-node-event-error-boundary.svg';
import {ReactComponent as FlowNodeEventErrorEnd} from 'modules/components/Icon/flow-node-event-error-end.svg';

import {ReactComponent as FlowNodeEventSubprocess} from 'modules/components/Icon/flow-node-subprocess-event.svg';

import {ReactComponent as FlowNodeEventTerminateEnd} from 'modules/components/Icon/flow-node-event-terminate-end.svg';
import {ReactComponent as FlowNodeEscalationEndEvent} from 'modules/components/Icon/flow-node-escalation-end-event.svg';
import {ReactComponent as FlowNodeEscalationBoundaryEvent} from 'modules/components/Icon/flow-node-escalation-boundary-event.svg';
import {ReactComponent as FlowNodeEscalationBoundaryNonInterruptingEvent} from 'modules/components/Icon/flow-node-escalation-boundary-non-interrupting-event.svg';
import {ReactComponent as FlowNodeEscalationIntermediateThrowEvent} from 'modules/components/Icon/flow-node-escalation-intermediate-throw-event.svg';
import {ReactComponent as FlowNodeEscalationNonInterruptingStartEvent} from 'modules/components/Icon/flow-node-escalation-non-interrupting-start-event.svg';
import {ReactComponent as FlowNodeEscalationStartEvent} from 'modules/components/Icon/flow-node-escalation-start-event.svg';

import {ReactComponent as FlowNodeLinkEventIntermediateCatch} from 'modules/components/Icon/flow-node-link-event-intermediate-catch.svg';
import {ReactComponent as FlowNodeLinkEventIntermediateThrow} from 'modules/components/Icon/flow-node-link-event-intermediate-throw.svg';
import {ReactComponent as FlowNodeEventSignalStart} from 'modules/components/Icon/flow-node-event-signal-start.svg';

const getSVGComponent = (
  businessObject: BusinessObject,
  isMultiInstanceBody: boolean
) => {
  if (isMultiInstanceBody) {
    switch (getMultiInstanceType(businessObject)) {
      case 'parallel':
        return FlowNodeTaskParallel;
      case 'sequential':
        return FlowNodeTaskMulti;
    }
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
      return FlowNodeEventSignalStart;
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
      return isEventSubProcess(businessObject)
        ? FlowNodeEventSubprocess
        : FlowNodeTaskSubProcess;
  }

  return FlowNodeTask;
};

type Props = {
  flowNodeInstanceType: FlowNodeInstance['type'];
  diagramBusinessObject: BusinessObject;
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
    flowNodeInstanceType === 'MULTI_INSTANCE_BODY'
  );

  return (
    <SVGIcon
      SVGComponent={SVGComponent}
      $isGateway={['bpmn:ParallelGateway', 'bpmn:ExclusiveGateway'].includes(
        diagramBusinessObject.$type
      )}
      className={className}
      $hasLeftMargin={hasLeftMargin}
    />
  );
};

export {FlowNodeIcon};
