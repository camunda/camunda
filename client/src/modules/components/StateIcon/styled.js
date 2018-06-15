import styled, {css} from 'styled-components';
import {
  StateIconIncident,
  StateOk,
  StateCompleted
} from 'modules/components/Icon';
import {Colors} from 'modules/theme';

const iconPosition = css`
  position: relative;
  top: 3px;
  margin-right: 5px;
`;

export const IncidentIcon = styled(StateIconIncident)`
  ${iconPosition};
  color: ${Colors.incidentsAndErrors};
`;

export const ActiveIcon = styled(StateOk)`
  ${iconPosition};
  color: ${Colors.allIsWell};
`;

export const CompletedIcon = styled(StateCompleted)`
  ${iconPosition};
  color: ${Colors.allIsWell};
`;

export const CanceledIcon = styled(StateCompleted)`
  ${iconPosition};
`;
