import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'theme';

export const Footer = themed(styled.div`
  bottom: 0px;
  left: 0px;
  position: absolute;
  height: 38px;
  width: 100%;
  padding: 10px 0px;
  border-radius: 3px;
  border-top: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight06
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
`);
