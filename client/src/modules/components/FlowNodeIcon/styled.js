import styled, {css} from 'styled-components';
import {
  FlowNodeEndEvent,
  FlowNodeExclusiveGateway,
  FlowNodeParallelGateway,
  FlowNodeStartEvent,
  FlowNodeStateCanceledDark,
  FlowNodeStateCanceledLight,
  FlowNodeStateCanceledSelected,
  FlowNodeStateCompletedDark,
  FlowNodeStateCompletedLight,
  FlowNodeStateCompletedSelected,
  FlowNodeStateIncidentDark,
  FlowNodeStateIncidentLight,
  FlowNodeStateOkDark,
  FlowNodeStateOkLight,
  FlowNodeTaskDefault,
  FlowNodeTaskService,
  FlowNodeTaskUser
} from 'modules/components/Icon';

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
