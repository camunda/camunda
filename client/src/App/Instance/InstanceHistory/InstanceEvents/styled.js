import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';
import {InstanceHistoryIconIncidentActive} from 'modules/components/Icon';

export const InstanceEvents = themed(styled.div`
  flex: 1;
  position: relative;

  overflow: auto;
  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  border-top: none;
  border-bottom: none;
`);

export const EventsContainer = styled.ul`
  position: absolute;
  height: 100%;
  width: 100%;
  margin: 0;
  padding: 0;
`;

export const EventEntry = themed(styled.li`
  border-bottom: 1px solid
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  position: relative;
  display: flex;
  flex-direction: column;
`);

export const DataEntry = themed(styled.div`
  opacity: 0.9;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)'
  })};
  padding-top: 7px;
  padding-bottom: 7px;
  padding-left: ${({indentation}) => `${indentation * 22 + 37}px`};
`);

export const IncidentIcon = styled(InstanceHistoryIconIncidentActive)`
  position: absolute;
  right: 8px;
  top: 5px;
`;
