import styled from 'styled-components';

import {HEADER_HEIGHT} from './../Header/styled';
import {Colors, themed, themeStyle} from 'modules/theme';

import SplitPane from 'modules/components/SplitPane';

export const Instance = styled.main`
  display: flex;
  flex-direction: column;
  height: calc(100vh - ${HEADER_HEIGHT}px);
  position: relative;
`;

export const PaneBody = styled(SplitPane.Pane.Body)`
  flex-direction: row;
`;

export const Section = themed(styled.div`
  flex: 1;
  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  border-top: none;
  border-bottom: none;
`);

export const PaneFooter = styled(SplitPane.Pane.Footer)`
  text-align: right;
`;

export const FlowNodeInstanceLog = themed(styled.div`
  position: relative;
  width: auto;
  display: flex;
  flex: 1;
  overflow: auto;
  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  border-top: none;
  border-left: none;
  border-bottom: none;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)'
  })};
`);

export const NodeContainer = styled.div`
  position: absolute;
  height: 100%;
  width: inherit;
  min-width: 100%;
  min-height: min-content;
  margin: 0;
  padding: 0;
  padding-left: 8px;
`;
