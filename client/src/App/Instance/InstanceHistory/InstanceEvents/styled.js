import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

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

export const EventsContainer = styled.div`
  position: absolute;
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  min-height: min-content;
  padding-left: 15px;
`;

export const MetaDataEntry = themed(styled.p`
  opacity: 0.9;
  color: ${themeStyle({
    light: Colors.uiLight06
  })};
`);
