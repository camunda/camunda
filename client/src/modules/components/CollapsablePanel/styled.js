/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';
import BasicCollapseButton from 'modules/components/CollapseButton';

import {DIRECTION, PANEL_POSITION} from 'modules/constants';

import BasicPanel from '../Panel';

export const COLLAPSABLE_PANEL_MIN_WIDTH = '56px';

const overlayStyles = css`
  position: absolute;
  z-index: 2;
  box-shadow: 0 2px 4px 0 rgba(0, 0, 0, 0.5);

  ${({panelPosition}) => {
    if (panelPosition === PANEL_POSITION.RIGHT) return 'right: 0;';
    if (panelPosition === PANEL_POSITION.LEFT) return 'left: 0;';
  }}
`;

export const Collapsable = themed(styled.div`
  border-radius: ${({panelPosition}) => {
    if (panelPosition === PANEL_POSITION.RIGHT) return '3px 0 0 0';
    if (panelPosition === PANEL_POSITION.LEFT) return '0 3px 0 0';
  }};

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

  transition: width 0.2s ease-out;
`);

const panelStyle = css`
  border-radius: inherit;
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
`;

export const CollapsedPanel = styled(BasicPanel)`
  ${panelStyle}
  opacity: ${({isCollapsed}) => (isCollapsed ? '1' : '0')};
  visibility: ${({isCollapsed}) => (isCollapsed ? 'visible' : 'hidden')};
  z-index: ${({isCollapsed}) => (isCollapsed ? '1' : '0')};
`;

export const Header = styled(BasicPanel.Header)`
  border-radius: inherit;
  ${({panelPosition}) =>
    panelPosition === PANEL_POSITION.RIGHT && 'padding-left: 55px;'}
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

  border-radius: inherit;
  z-index: 2;
`;

export const ExpandButton = themed(styled.button`
  height: 100%;
  padding: 11px;

  background: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};
  color: ${themeStyle({
    dark: '#fff',
    light: Colors.uiLight06
  })};
  border: none;
  border-radius: inherit;

  opacity: 0.9;
  font-size: 15px;
  font-weight: bold;

  position: relative;
`);

export const Vertical = styled.span`
  padding-right: ${({offset}) => offset}px;
  position: absolute;
  top: 0;
  left: 0;
  transform: rotate(-90deg) translateX(-100%) translateY(100%);
  transform-origin: 0 0;
  display: flex;
  align-items: center;
  margin-top: 11px;
`;
