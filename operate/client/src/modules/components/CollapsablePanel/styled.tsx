/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {
  RowCollapse as BaseRowCollapse,
  RowExpand as BaseRowExpand,
} from '@carbon/react/icons';
import {IconButton as BaseIconButton} from '@carbon/react';
import {Header as BaseHeader} from '../PanelHeader/styled';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/constants';
import {zOverlayCollapsable} from 'modules/constants/componentHierarchy';

type CollapsableProps = {
  $isCollapsed: boolean;
  $panelPosition: 'LEFT' | 'RIGHT';
  $isOverlay?: boolean;
  $maxWidth: number;
};

const Collapsable = styled.section<CollapsableProps>`
  ${({$isCollapsed, $isOverlay, $panelPosition, $maxWidth}) => {
    const isLeft = $panelPosition === 'LEFT';
    const isRight = $panelPosition === 'RIGHT';

    return css`
      height: 100%;

      ${$isCollapsed
        ? css`
            min-width: ${COLLAPSABLE_PANEL_MIN_WIDTH};
          `
        : css`
            min-width: ${$maxWidth}px;
            width: ${$maxWidth}px;
          `};

      ${$isOverlay
        ? css`
            position: absolute;
            z-index: ${zOverlayCollapsable};
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

type Props = {
  $isClickable?: boolean;
  $panelPosition: 'RIGHT' | 'LEFT';
};

const Panel = styled.div<Props>`
  ${({$isClickable, $panelPosition}) => {
    return css`
      height: 100%;
      background-color: var(--cds-layer);
      display: flex;
      flex-direction: column;

      ${$panelPosition === 'LEFT'
        ? css`
            border-right: 1px solid var(--cds-border-subtle-01);
          `
        : css`
            border-left: 1px solid var(--cds-border-subtle-01);
          `};

      ${$isClickable &&
      css`
        padding: var(--cds-spacing-03);
        cursor: pointer;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--cds-spacing-06);
      `}
    `;
  }}
`;

type IconProps = {
  $panelPosition: 'RIGHT' | 'LEFT';
};

const IconStyles = css<Props>`
  ${({$panelPosition}) => {
    return css`
      ${$panelPosition === 'LEFT' &&
      css`
        transform: rotate(-90deg);
      `}
      ${$panelPosition === 'RIGHT' &&
      css`
        transform: rotate(90deg);
      `}
    `;
  }}
`;

const ExpandIcon = styled(BaseRowExpand)<IconProps>`
  ${IconStyles}
`;

const CollapseIcon = styled(BaseRowCollapse)<IconProps>`
  ${IconStyles}
`;

const IconButton = styled(BaseIconButton)`
  padding: 0px !important;
  min-height: 0;
  align-items: center;
`;

type HeaderProps = {
  $panelPosition: 'RIGHT' | 'LEFT';
};

const Header = styled(BaseHeader)<HeaderProps>`
  ${({$panelPosition}) => {
    return css`
      justify-content: space-between;
      ${$panelPosition === 'RIGHT' &&
      css`
        flex-direction: row-reverse;
        justify-content: flex-end;
        gap: var(--cds-spacing-06);
      `}
    `;
  }}
`;

type ContentProps = {
  $scrollable: boolean;
};

const Content = styled.div<ContentProps>`
  flex-grow: 1;
  ${({$scrollable}) => {
    return css`
      position: relative;
      overflow: ${$scrollable ? 'auto' : 'hidden'};
    `;
  }}
`;

export {
  Panel,
  Collapsable,
  ExpandIcon,
  CollapseIcon,
  IconButton,
  Header,
  Content,
};
