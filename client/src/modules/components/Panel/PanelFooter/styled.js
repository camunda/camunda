import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Footer = themed(styled.div`
  display: flex;
  height: 38px;
  width: 100%;
  padding: 10px 20px;
  border-radius: 3px;
  border-top: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
`);
