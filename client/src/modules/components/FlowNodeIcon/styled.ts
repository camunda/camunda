/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

import {ReactComponent as FlowNodeProcess} from 'modules/components/Icon/flow-node-process-root.svg';

/**
 * Gateway Imports
 */
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

const newIconStyle: ThemedInterpolationFunction = ({theme}) => {
  return css`
    position: relative;
    color: ${theme.colors.text02};
    top: 0px;
    left: 3px;
  `;
};

const PROCESS = styled(FlowNodeProcess)`
  ${newIconStyle}
`;

/**
 * Gateways
 */

const GATEWAY_EXCLUSIVE = styled(FlowNodeGatewayExclusive)`
  ${newIconStyle}
  top: 3px;
  left: 0px;
`;

const GATEWAY_PARALLEL = styled(FlowNodeGatewayParallel)`
  ${newIconStyle}
  top: 3px;
  left: 0px;
`;

const GATEWAY_EVENT_BASED = styled(FlowNodeGatewayEventBased)`
  ${newIconStyle}
  left: 3px;
`;

/**
 * Task Icons
 */

const TASK_SERVICE = styled(FlowNodeTaskService)`
  ${newIconStyle};
`;

const TASK_RECEIVE = styled(FlowNodeTaskReceive)`
  ${newIconStyle};
`;

const TASK_SEND = styled(FlowNodeTaskSend)`
  ${newIconStyle};
`;

const TASK_DEFAULT = styled(FlowNodeTask)`
  ${newIconStyle};
`;

const TASK_SUBPROCESS = styled(FlowNodeTaskSubProcess)`
  ${newIconStyle};
`;

const TASK_CALL_ACTIVITY = styled(FlowNodeCallActivity)`
  ${newIconStyle};
`;

const TASK_USER = styled(FlowNodeTaskUser)`
  ${newIconStyle};
`;

const TASK_MANUAL = styled(FlowNodeTaskManual)`
  ${newIconStyle};
`;

const TASK_BUSINESS_RULE = styled(FlowNodeTaskBusinessRule)`
  ${newIconStyle};
`;

const TASK_SCRIPT = styled(FlowNodeTaskScript)`
  ${newIconStyle};
`;

/**
 * Multi Instance Icons
 */

const MULTI_SEQUENTIAL = styled(FlowNodeTaskMulti)`
  ${newIconStyle};
`;

const MULTI_PARALLEL = styled(FlowNodeTaskParallel)`
  ${newIconStyle};
`;

/**
 * Event Icons
 */

const START = styled(FlowNodeEventStart)`
  ${newIconStyle}
`;

const END = styled(FlowNodeEventEnd)`
  ${newIconStyle}
  padding: 1px;
`;

const INTERMEDIATE_THROW = styled(FlowNodeEventIntermediateThrow)`
  ${newIconStyle}
`;

/**
 * Message Event Icons
 */

const EVENT_MESSAGE_START = styled(FlowNodeEventMessageStart)`
  ${newIconStyle}
`;

const EVENT_MESSAGE_END = styled(FlowNodeEventMessageEnd)`
  ${newIconStyle}
`;

/**
 * Error Event Icons
 */
const EVENT_ERROR_START = styled(FlowNodeEventErrorStart)`
  ${newIconStyle}
`;

const EVENT_ERROR_BOUNDARY_INTERRUPTING = styled(FlowNodeEventErrorBoundary)`
  ${newIconStyle}
`;

const EVENT_ERROR_END = styled(FlowNodeEventErrorEnd)`
  ${newIconStyle}
`;

/**
 * Other Icons
 */

const EVENT_SUBPROCESS = styled(FlowNodeEventSubprocess)`
  ${newIconStyle}
`;

// uses the same style as boundary interrupting
const EVENT_MESSAGE_INTERMEDIATE_CATCH = styled(
  FlowNodeEventMessageBoundaryInterrupting
)`
  ${newIconStyle}
`;

const EVENT_MESSAGE_INTERMEDIATE_THROW = styled(
  FlowNodeEventMessageIntermediateThrow
)`
  ${newIconStyle}
`;

const EVENT_MESSAGE_BOUNDARY_INTERRUPTING = styled(
  FlowNodeEventMessageBoundaryInterrupting
)`
  ${newIconStyle}
`;

const EVENT_MESSAGE_BOUNDARY_NON_INTERRUPTING = styled(
  FlowNodeEventMessageBoundaryNonInterrupting
)`
  ${newIconStyle}
`;

/**
 * Timer Event Icons
 */

const EVENT_TIMER_START = styled(FlowNodeEventTimerStart)`
  ${newIconStyle}
`;

const EVENT_TIMER_INTERMEDIATE_CATCH = styled(
  FlowNodeEventTimerBoundaryInterrupting
)`
  ${newIconStyle}
`;

const EVENT_TIMER_BOUNDARY_NON_INTERRUPTING = styled(
  FlowNodeEventTimerBoundaryNonInerrupting
)`
  ${newIconStyle}
`;

const EVENT_TIMER_BOUNDARY_INTERRUPTING = styled(
  FlowNodeEventTimerBoundaryInterrupting
)`
  ${newIconStyle}
`;

export {
  PROCESS,
  GATEWAY_EXCLUSIVE,
  GATEWAY_PARALLEL,
  GATEWAY_EVENT_BASED,
  TASK_SERVICE,
  TASK_RECEIVE,
  TASK_SEND,
  TASK_DEFAULT,
  TASK_SUBPROCESS,
  TASK_CALL_ACTIVITY,
  TASK_USER,
  TASK_BUSINESS_RULE,
  TASK_SCRIPT,
  TASK_MANUAL,
  MULTI_SEQUENTIAL,
  MULTI_PARALLEL,
  START,
  END,
  INTERMEDIATE_THROW,
  EVENT_MESSAGE_START,
  EVENT_MESSAGE_END,
  EVENT_ERROR_START,
  EVENT_ERROR_BOUNDARY_INTERRUPTING,
  EVENT_ERROR_END,
  EVENT_SUBPROCESS,
  EVENT_MESSAGE_INTERMEDIATE_CATCH,
  EVENT_MESSAGE_INTERMEDIATE_THROW,
  EVENT_MESSAGE_BOUNDARY_INTERRUPTING,
  EVENT_MESSAGE_BOUNDARY_NON_INTERRUPTING,
  EVENT_TIMER_START,
  EVENT_TIMER_INTERMEDIATE_CATCH,
  EVENT_TIMER_BOUNDARY_NON_INTERRUPTING,
  EVENT_TIMER_BOUNDARY_INTERRUPTING,
};
