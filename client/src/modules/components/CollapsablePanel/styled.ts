/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';
import {rgba} from 'polished';

import BasicCollapseButton from 'modules/components/CollapseButton';
import BasicPanel from '../Panel';

const COLLAPSABLE_PANEL_MIN_WIDTH = '56px';
type PanelPosition = 'RIGHT' | 'LEFT';

type CollapsableProps = {
  panelPosition?: PanelPosition;
  isOverlay?: boolean;
  maxWidth?: number;
  isCollapsed?: boolean;
};

const Collapsable = styled.div<CollapsableProps>`
  ${({theme, panelPosition, isOverlay, maxWidth, isCollapsed}) => {
    const colors = theme.colors.modules.collapsablePanel.collapsable;
    const isLeft = panelPosition === 'LEFT';
    const isRight = panelPosition === 'RIGHT';

    return css`
      position: relative;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      min-width: ${isCollapsed
        ? COLLAPSABLE_PANEL_MIN_WIDTH
        : css`
            ${maxWidth}px
          `};
      height: 100%;
      background-color: ${colors.backgroundColor};
      transition: width 0.2s ease-out;

      ${isOverlay
        ? css`
            position: absolute;
            z-index: 2;
            box-shadow: 0 2px 4px 0 ${rgba(theme.colors.black, 0.5)};
            ${isRight
              ? css`
                  right: 0;
                `
              : ''}
            ${isLeft
              ? css`
                  left: 0;
                `
              : ''}
          `
        : ''};
    `;
  }}
`;

type PanelStyleProps = {
  transitionTimeout?: number;
};

const panelStyle: ThemedInterpolationFunction<PanelStyleProps> = ({
  transitionTimeout,
}) => {
  return css`
    border-radius: inherit;
    position: absolute;
    top: 0;
    left: 0;
    height: 100%;
    width: 100%;
    transition: visibility ${transitionTimeout}ms ease-out,
      opacity ${transitionTimeout}ms ease-out;
  `;
};

type ExpandedPanelProps = {
  isCollapsed?: boolean;
  panelPosition?: PanelPosition;
  hasBackgroundColor?: boolean;
  transitionTimeout?: number;
};

const ExpandedPanel = styled(BasicPanel)<ExpandedPanelProps>`
  ${({theme, isCollapsed, panelPosition, hasBackgroundColor}) => {
    return css`
      opacity: ${isCollapsed ? 0 : 1};
      visibility: ${isCollapsed ? 'hidden' : 'visible'};
      z-index: ${isCollapsed ? 0 : 1};
      ${panelStyle}
      ${panelPosition === 'RIGHT'
        ? css`
            border-right: none;
          `
        : ''}
      ${hasBackgroundColor
        ? css`
            background-color: ${theme.colors.ui02};
          `
        : ''}
    `;
  }}
`;

type CollapsedPanelProps = {
  isCollapsed?: boolean;
  transitionTimeout?: number;
};

const CollapsedPanel = styled(BasicPanel)<CollapsedPanelProps>`
  ${({isCollapsed}) => {
    return css`
      opacity: ${isCollapsed ? 1 : 0};
      visibility: ${isCollapsed ? 'visible' : 'hidden'};
      z-index: ${isCollapsed ? 1 : 0};
      ${panelStyle}
    `;
  }}
`;

type HeaderProps = {
  panelPosition?: PanelPosition;
};

const Header = styled(BasicPanel.Header)<HeaderProps>`
  ${({panelPosition}) => {
    return css`
      border-radius: inherit;
      ${panelPosition === 'RIGHT'
        ? css`
            padding-left: 55px;
          `
        : ''}
    `;
  }}
`;

type CollapseButtonProps = {
  direction?: 'UP' | 'DOWN' | 'RIGHT' | 'LEFT';
};

const CollapseButton = styled(BasicCollapseButton)<CollapseButtonProps>`
  ${({direction}) => {
    return css`
      position: absolute;
      top: 0;
      border-top: none;
      border-bottom: none;
      border-radius: inherit;
      z-index: 2;

      ${direction === 'LEFT'
        ? css`
            border-right: none;
            right: 0;
          `
        : ''}
      ${direction === 'RIGHT'
        ? css`
            border-left: none;
            left: 0;
          `
        : ''}
    `;
  }}
`;

const ExpandButton = styled.button`
  ${({theme}) => {
    const colors = theme.colors.modules.collapsablePanel.expandButton;

    return css`
      height: 100%;
      padding: 11px;
      background: ${colors.backgroundColor};
      color: ${theme.colors.text01};
      border: none;
      border-radius: inherit;
      opacity: 0.9;
      font-size: 16px;
      font-weight: 600;
      position: relative;
    `;
  }}
`;

type VerticalProps = {
  offset: number;
};

const Vertical = styled.span<VerticalProps>`
  ${({offset}) => {
    return css`
      padding-right: ${offset}px;
      position: absolute;
      top: 0;
      left: 0;
      transform: rotate(-90deg) translateX(-100%) translateY(100%);
      transform-origin: 0 0;
      display: flex;
      align-items: center;
      margin-top: 11px;
    `;
  }}
`;

export {
  COLLAPSABLE_PANEL_MIN_WIDTH,
  Collapsable,
  ExpandedPanel,
  CollapsedPanel,
  Header,
  CollapseButton,
  ExpandButton,
  Vertical,
};
