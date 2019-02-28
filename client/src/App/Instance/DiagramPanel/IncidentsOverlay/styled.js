import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import {INCIDENTS_BAR_HEIGHT} from 'modules/constants';

export const Overlay = themed(styled.div`
  width: 100%;
  height: 100%;
  padding-top: ${INCIDENTS_BAR_HEIGHT}px;
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
