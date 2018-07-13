import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

import SplitPane from 'modules/components/SplitPane';

export const PaneBody = styled(SplitPane.Pane.Body)`
  display: flex;
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
  display: flex;
  justify-content: flex-end;
`;
