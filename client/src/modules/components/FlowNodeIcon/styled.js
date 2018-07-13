import styled, {css} from 'styled-components';

import {Colors} from 'modules/theme';

import {
  FlownodeActivity as BasicFlownodeActivity,
  FlownodeActivityIncident as BasicFlownodeActivityIncident,
  FlownodeActivityCompleted as BasicFlownodeActivityCompleted,
  FlownodeEvent as BasicFlownodeEvent,
  FlownodeEventIncident as BasicFlownodeEventIncident,
  FlownodeEventCompleted as BasicFlownodeEventCompleted,
  FlownodeGateway as BasicFlownodeGateway,
  FlownodeGatewayIncident as BasicFlownodeGatewayIncident,
  FlownodeGatewayCompleted as BasicFlownodeGatewayCompleted,
  InstanceHistoryIconCancelDark as BasicInstanceHistoryIconCancelDark
} from 'modules/components/Icon';

const sizeStyle = css`
  width: 16px;
  height: 16px;
`;

const incidentStyle = css`
  color: ${Colors.incidentsAndErrors};
`;

export const FlownodeActivity = styled(BasicFlownodeActivity)`
  ${sizeStyle};
`;

export const FlownodeActivityIncident = styled(BasicFlownodeActivityIncident)`
  ${sizeStyle};

  ${incidentStyle};
`;

export const FlownodeActivityCompleted = styled(BasicFlownodeActivityCompleted)`
  ${sizeStyle};
`;

export const FlownodeEvent = styled(BasicFlownodeEvent)`
  ${sizeStyle};
`;

export const FlownodeEventIncident = styled(BasicFlownodeEventIncident)`
  ${sizeStyle};

  ${incidentStyle};
`;

export const FlownodeEventCompleted = styled(BasicFlownodeEventCompleted)`
  ${sizeStyle};
`;

export const FlownodeGateway = styled(BasicFlownodeGateway)`
  ${sizeStyle};
`;

export const FlownodeGatewayIncident = styled(BasicFlownodeGatewayIncident)`
  ${sizeStyle};

  ${incidentStyle};
`;

export const FlownodeGatewayCompleted = styled(BasicFlownodeGatewayCompleted)`
  ${sizeStyle};
`;

export const InstanceHistoryIconCancelDark = styled(
  BasicInstanceHistoryIconCancelDark
)`
  width: 20px;
  height: 20px;
`;
