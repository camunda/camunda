/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {ThemedInterpolationFunction, css} from 'styled-components';
import {
  RowCollapse as BaseRowCollapse,
  RowExpand as BaseRowExpand,
} from '@carbon/react/icons';
import {IconButton as BaseIconButton} from '@carbon/react';
import {Header as BaseHeader} from '../PanelHeader/styled';

type CollapsableProps = {
  isCollapsed: boolean;
};

const Collapsable = styled.div<CollapsableProps>`
  ${({isCollapsed}) => {
    return css`
      height: 100%;

      ${isCollapsed
        ? css`
            min-width: var(--cds-spacing-09);
          `
        : css`
            min-width: calc(2 * var(--cds-spacing-13));
          `};
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
      background-color: var(--cds-layer-01);

      ${$panelPosition === 'LEFT'
        ? css`
            border-right: 1px solid var(--cds-border-subtle-01);
          `
        : css`
            border-left: 1px solid var(--cds-border-subtle-01);
          `};

      ${$isClickable &&
      css`
        padding: var(--cds-spacing-04);
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

const IconStyles: ThemedInterpolationFunction<IconProps> = ({
  $panelPosition,
}) => {
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
};

const ExpandIcon = styled(BaseRowExpand)<IconProps>`
  ${IconStyles}
`;

const CollapseIcon = styled(BaseRowCollapse)<IconProps>`
  ${IconStyles}
`;

const IconButton = styled(BaseIconButton)`
  padding: 0px !important;
  min-height: 0;
`;

const Header = styled(BaseHeader)`
  justify-content: space-between;
`;

export {Panel, Collapsable, ExpandIcon, CollapseIcon, IconButton, Header};
