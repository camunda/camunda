/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

import {ReactComponent as FlowNodeEndEvent} from 'modules/components/Icon/flow-node-event-end.svg';
import {ReactComponent as FlowNodeExclusiveGateway} from 'modules/components/Icon/flow-node-gateway-exclusive.svg';
import {ReactComponent as FlowNodeParallelGateway} from 'modules/components/Icon/flow-node-gateway-parallel.svg';
import {ReactComponent as FlowNodeStartEvent} from 'modules/components/Icon/flow-node-event-start.svg';
import {ReactComponent as FlowNodeTaskDefault} from 'modules/components/Icon/flow-node-task-default.svg';
import {ReactComponent as FlowNodeTaskService} from 'modules/components/Icon/flow-node-task-service.svg';
import {ReactComponent as FlowNodeTaskUser} from 'modules/components/Icon/flow-node-task-user.svg';
import {ReactComponent as FlowNodeWorkFlow} from 'modules/components/Icon/document.svg';

const iconStyle = css`
  position: relative;
  top: 3px;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
`;

// --------------
// Flow node type icons
// --------------

export const EndEvent = themed(styled(FlowNodeEndEvent)`
  ${iconStyle};
`);

export const ExclusiveGateway = themed(styled(FlowNodeExclusiveGateway)`
  ${iconStyle};
`);

export const ParallelGateway = themed(styled(FlowNodeParallelGateway)`
  ${iconStyle};
`);

export const StartEvent = themed(styled(FlowNodeStartEvent)`
  ${iconStyle};
`);

export const TaskDefault = themed(styled(FlowNodeTaskDefault)`
  ${iconStyle};
`);

export const TaskService = themed(styled(FlowNodeTaskService)`
  ${iconStyle};
`);
export const TaskUser = themed(styled(FlowNodeTaskUser)`
  ${iconStyle};
`);

export const SubProcess = themed(styled(FlowNodeTaskDefault)`
  ${iconStyle};
`);

export const Workflow = themed(styled(FlowNodeWorkFlow)`
  ${iconStyle};
  top: 0px;
  left: 3px;
  margin: 0 4px;
  height: 18px;
  width: auto;
`);
