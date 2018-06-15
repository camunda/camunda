import styled, {css} from 'styled-components';
import {
  StateIconIncident,
  StateOk,
  StateCompleted,
  Stop
} from 'modules/components/Icon';
import {Colors, themed, themeStyle} from 'modules/theme';

const iconPosition = css`
  position: relative;
  top: 3px;
  margin-right: 5px;
`;

export const IncidentIcon = styled(StateIconIncident)`
  ${iconPosition};
  color: ${Colors.incidentsAndErrors};
`;

export const ActiveIcon = themed(styled(StateOk)`
  ${iconPosition};
  color: ${Colors.allIsWell};
`);

export const CompletedIcon = themed(styled(StateCompleted)`
  ${iconPosition};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
  opacity: ${themeStyle({
    dark: '0.46',
    light: '0.4'
  })};
`);

export const CanceledIcon = themed(styled(Stop)`
  ${iconPosition};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
  opacity: ${themeStyle({
    dark: '0.81',
    light: '0.75'
  })};
`);
