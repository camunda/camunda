import styled, {css} from 'styled-components';
import {ReactComponent as FlowNodeEndEvent} from 'modules/components/Icon/flow-node-end-event.svg';
import {ReactComponent as FlowNodeExclusiveGateway} from 'modules/components/Icon/flow-node-exclusive-gateway.svg';
import {ReactComponent as FlowNodeParallelGateway} from 'modules/components/Icon/flow-node-parallel-gateway.svg';
import {ReactComponent as FlowNodeStartEvent} from 'modules/components/Icon/flow-node-start-event.svg';
import {ReactComponent as FlowNodeStateCanceledDark} from 'modules/components/Icon/flow-node-state-canceled-dark.svg';
import {ReactComponent as FlowNodeStateCanceledLight} from 'modules/components/Icon/flow-node-state-canceled-light.svg';
import {ReactComponent as FlowNodeStateCanceledSelected} from 'modules/components/Icon/flow-node-state-canceled-selected.svg';
import {ReactComponent as FlowNodeStateCompletedDark} from 'modules/components/Icon/flow-node-state-completed-dark.svg';
import {ReactComponent as FlowNodeStateCompletedLight} from 'modules/components/Icon/flow-node-state-completed-light.svg';
import {ReactComponent as FlowNodeStateCompletedSelected} from 'modules/components/Icon/flow-node-state-completed-selected.svg';
import {ReactComponent as FlowNodeStateIncidentDark} from 'modules/components/Icon/flow-node-state-incident-dark.svg';
import {ReactComponent as FlowNodeStateIncidentLight} from 'modules/components/Icon/flow-node-state-incident-light.svg';
import {ReactComponent as FlowNodeStateOkDark} from 'modules/components/Icon/flow-node-state-ok-dark.svg';
import {ReactComponent as FlowNodeStateOkLight} from 'modules/components/Icon/flow-node-state-ok-light.svg';
import {ReactComponent as FlowNodeTaskDefault} from 'modules/components/Icon/flow-node-task-default.svg';
import {ReactComponent as FlowNodeTaskService} from 'modules/components/Icon/flow-node-task-service.svg';
import {ReactComponent as FlowNodeTaskUser} from 'modules/components/Icon/flow-node-task-user.svg';

export const IconContainer = styled.span`
  width: 26px;
  height: 26px;
  position: relative;
  display: inline-block;
`;

// --------------
// Flow node type icons
// --------------

const flowNodeTypeIconStyle = css`
  position: absolute;
`;

export const EndEvent = styled(FlowNodeEndEvent)`
  ${flowNodeTypeIconStyle};
`;
export const ExclusiveGateway = styled(FlowNodeExclusiveGateway)`
  ${flowNodeTypeIconStyle};
`;
export const ParallelGateway = styled(FlowNodeParallelGateway)`
  ${flowNodeTypeIconStyle};
`;
export const StartEvent = styled(FlowNodeStartEvent)`
  ${flowNodeTypeIconStyle};
`;
export const TaskDefault = styled(FlowNodeTaskDefault)`
  ${flowNodeTypeIconStyle};
`;
export const TaskService = styled(FlowNodeTaskService)`
  ${flowNodeTypeIconStyle};
`;
export const TaskUser = styled(FlowNodeTaskUser)`
  ${flowNodeTypeIconStyle};
`;

// -----------
// State icons
// ------------

const stateIconStyle = css`
  position: absolute;
  bottom: 0;
  left: 0;
`;

export const CanceledDark = styled(FlowNodeStateCanceledDark)`
  ${stateIconStyle};
`;
export const CanceledLight = styled(FlowNodeStateCanceledLight)`
  ${stateIconStyle};
`;
export const CanceledSelected = styled(FlowNodeStateCanceledSelected)`
  ${stateIconStyle};
`;
export const CompletedDark = styled(FlowNodeStateCompletedDark)`
  ${stateIconStyle};
`;
export const CompletedLight = styled(FlowNodeStateCompletedLight)`
  ${stateIconStyle};
`;
export const CompletedSelected = styled(FlowNodeStateCompletedSelected)`
  ${stateIconStyle};
`;
export const IncidentDark = styled(FlowNodeStateIncidentDark)`
  ${stateIconStyle};
`;
export const IncidentLight = styled(FlowNodeStateIncidentLight)`
  ${stateIconStyle};
`;
export const OkDark = styled(FlowNodeStateOkDark)`
  ${stateIconStyle};
`;
export const OkLight = styled(FlowNodeStateOkLight)`
  ${stateIconStyle};
`;
