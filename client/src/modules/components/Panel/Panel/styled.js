import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'theme';

export const Panel = themed(styled.div`
  width: 100%;
  height: 100%;
  position: relative;
  border-radius: 3px;
  border: solid 1px #45464e;
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight05
  })};
`);
