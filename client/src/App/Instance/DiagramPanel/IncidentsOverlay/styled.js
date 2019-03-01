import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import {INCIDENTS_BAR_HEIGHT} from 'modules/constants';

import bg_light from './bg_light.png';
import bg_dark from './bg_dark.png';

export const Overlay = themed(styled.div`
  width: 100%;
  height: 100%;
  padding-top: ${INCIDENTS_BAR_HEIGHT}px;
  padding-left: 51px;
  position: absolute;
  z-index: 3;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;

  background-image: ${themeStyle({
    dark: `url(${bg_dark})`,
    light: `url(${bg_light})`
  })};
  background-repeat: repeat-y;
  background-position: fixed;
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04
  })};
`);
