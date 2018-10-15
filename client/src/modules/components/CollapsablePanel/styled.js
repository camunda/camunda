import styled, {css} from 'styled-components';

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

const panelStyle = css`
position: absolute;
top: 0;
left: 0;
height: 100%;
width 100%;
transition: opacity 0.2s ease-out;
`;

export const ExpandedPanel = styled(BasicPanel)`
  ${panelStyle}
  opacity: ${({isCollapsed}) => (isCollapsed ? '0' : '1')};
`;

export const CollapsedPanel = styled(BasicPanel)`
  ${panelStyle}
  opacity: ${({isCollapsed}) => (isCollapsed ? '1' : '0')};
`;
