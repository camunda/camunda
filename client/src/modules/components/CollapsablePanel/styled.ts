/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {rgba} from 'polished';
import BasicCollapseButton from 'modules/components/CollapseButton';
import {Panel as BasePanel} from 'modules/components/Panel';
import {styles} from '@carbon/elements';
import {PAGE_TOP_PADDING} from 'modules/constants';
import {zOverlayCollapsable} from 'modules/constants/componentHierarchy';

const COLLAPSABLE_PANEL_MIN_WIDTH = '56px';
type PanelPosition = 'RIGHT' | 'LEFT';

type CollapsableProps = {
  panelPosition?: PanelPosition;
  isOverlay?: boolean;
  maxWidth?: number;
  isCollapsed?: boolean;
  transitionTimeout?: number;
};

const Collapsable = styled.div<CollapsableProps>`
  ${({
    theme,
    panelPosition,
    isOverlay,
    maxWidth,
    isCollapsed,
    transitionTimeout,
  }) => {
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
      background-color: ${colors.backgroundColor};

      ${isOverlay
        ? css`
            position: absolute;
            height: calc(100% - ${PAGE_TOP_PADDING}px);
            top: ${PAGE_TOP_PADDING}px;
            z-index: ${zOverlayCollapsable};
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

      transition: min-width ${transitionTimeout}ms ease-out;

      & ${CollapsedPanel}, & ${ExpandedPanel} {
        border-radius: inherit;
        position: absolute;
        top: 0;
        left: 0;
        height: 100%;
        width: 100%;
      }
    `;
  }}
`;

type ExpandedPanelProps = {
  panelPosition?: PanelPosition;
  hasBackgroundColor?: boolean;
};

const ExpandedPanel = styled(BasePanel)<ExpandedPanelProps>`
  ${({theme, panelPosition, hasBackgroundColor}) => {
    return css`
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

const CollapsedPanel = styled(BasePanel)``;

type HeaderProps = {
  panelPosition?: PanelPosition;
};

const Header = styled(BasePanel.Header)<HeaderProps>`
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
      ${styles.productiveHeading02};
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
      left: -3px;
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
