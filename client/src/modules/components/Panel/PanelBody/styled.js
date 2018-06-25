import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

export const Body = themed(styled.div`
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  border-top: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
`);
