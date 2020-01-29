/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';
import BasicCollapseButton from 'modules/components/CollapseButton';
import VerticalCollapseButton from 'modules/components/VerticalCollapseButton';

import {DIRECTION, PANEL_POSITION} from 'modules/constants';

import BasicPanel from '../Panel';

export const COLLAPSABLE_PANEL_MIN_WIDTH = '56px';

const overlayStyles = css`
  position: absolute;

  ${({position}) => {
    if (position === PANEL_POSITION.RIGHT) return 'right: 0;';
    if (position === PANEL_POSITION.LEFT) return 'left: 0;';
  }}
`;

export const Collapsable = themed(styled.div`
  position: relative;

  display: flex;
  flex-direction: column;

  ${({isOverlay}) => (isOverlay ? overlayStyles : '')};

  overflow: hidden;
  width: ${({isCollapsed, maxWidth}) =>
    isCollapsed ? COLLAPSABLE_PANEL_MIN_WIDTH : `${maxWidth}px`};
  height: 100%;

  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};

  border-radius: ${({position}) => {
    if (position === PANEL_POSITION.RIGHT) return '3px 0 0 0';
    if (position === PANEL_POSITION.LEFT) return '0 3px 0 0';
  }};

  transition: width 0.2s ease-out;
`);

const panelStyle = css`
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 100%;
  transition: ${({transitionTimeout, isCollapsed}) =>
    `visibility  ${transitionTimeout}ms ease-out, opacity ${transitionTimeout}ms ease-out`};
`;

export const ExpandedPanel = styled(BasicPanel)`
  ${panelStyle}
  opacity: ${({isCollapsed}) => (isCollapsed ? '0' : '1')};
  visibility: ${({isCollapsed}) => (isCollapsed ? 'hidden' : 'visible')};
  z-index: ${({isCollapsed}) => (isCollapsed ? '0' : '1')};
  border-radius: 3px 3px 0 0;
`;

export const CollapsedPanel = styled(BasicPanel)`
  ${panelStyle}
  opacity: ${({isCollapsed}) => (isCollapsed ? '1' : '0')};
  visibility: ${({isCollapsed}) => (isCollapsed ? 'visible' : 'hidden')};
  z-index: ${({isCollapsed}) => (isCollapsed ? '1' : '0')};

`;

export const Header = styled(BasicPanel.Header)`
  ${({position}) => position === DIRECTION.RIGHT && 'padding-left: 55px;'}
`;

const CollapsedButtonLeft = css`
  border-right: none;
  right: 0;
`;

const CollapsedButtonRight = css`
  border-left: none;
  left: 0;
`;

export const CollapseButton = styled(BasicCollapseButton)`
  position: absolute;
  top: 0;
  border-top: none;
  border-bottom: none;

  ${({direction}) => {
    if (direction === DIRECTION.LEFT) return CollapsedButtonLeft;
    if (direction === DIRECTION.RIGHT) return CollapsedButtonRight;
  }}

  z-index: 2;
`;

export const VerticalButton = styled(VerticalCollapseButton)`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: 0 3px 0 0;
`;
