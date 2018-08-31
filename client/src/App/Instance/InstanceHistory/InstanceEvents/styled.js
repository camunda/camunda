import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

import Foldable from './Foldable';

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
`;

export const EventEntry = themed(styled.div`
  padding-left: 15px;
  border-bottom: 1px solid
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
`);

export const DataEntry = themed(styled.div`
  opacity: 0.9;
  color: ${themeStyle({
    light: Colors.uiLight06
  })};
  padding-top: 7px;
  padding-bottom: 7px;
`);

export const GroupFoldableSummary = styled(Foldable.Summary)`
  font-weight: bold;
`;
