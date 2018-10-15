import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

import BasicPanel from '../Panel';

export const Collapsable = themed(styled.div`
  overflow: hidden;
  transition: width 0.2s ease-out;
  width: ${({isCollapsed, maxWidth}) =>
    isCollapsed ? '56px' : `${maxWidth}px`};
  height: 100%;
  position: relative;
  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};
`);

export const Panel = styled(BasicPanel)`
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width 100%;
  transition: opacity 0.2s ease-out;
  opacity: ${({isCollapsed}) => (isCollapsed ? '0' : '1')};
`;
