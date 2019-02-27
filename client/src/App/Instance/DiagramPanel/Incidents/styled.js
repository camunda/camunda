import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Wrapper = themed(styled.div`
  width: 100%;
  height: 100%;
  padding-top: 42px;
  position: absolute;
  z-index: 3;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;

  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04
  })};
`);
