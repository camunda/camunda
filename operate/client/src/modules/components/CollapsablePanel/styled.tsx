/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
