import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Panel = themed(styled.div`
  display: flex;
  flex-direction: column;
  width: 100%;
  border-radius: 3px;
  border: solid 1px #45464e;
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight05
  })};
`);
