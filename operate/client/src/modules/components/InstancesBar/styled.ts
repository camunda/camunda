/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
import {styles} from '@carbon/elements';

type Size = 'small' | 'medium' | 'large';

type WrapperProps = {
  $size: Size;
};

const getFontStyle = ({$size}: WrapperProps) => {
  return css`
    ${$size === 'small' &&
    css`
      ${styles.bodyCompact01};
      color: var(--cds-text-secondary);
    `}
    ${$size === 'medium' &&
    css`
      ${styles.heading01};
      color: var(--cds-text-primary);
    `}
    ${$size === 'large' &&
    css`
      ${styles.heading02};
      color: var(--cds-text-primary);
    `}
  `;
};

const Wrapper = styled.div<WrapperProps>`
  ${({$size}) => {
    return css`
      display: flex;
      ${getFontStyle({$size})};
    `;
  }}
`;

type IncidentsCountProps = {
  $hasIncidents?: boolean;
};

const IncidentsCount = styled.div<IncidentsCountProps>`
  ${({$hasIncidents}) => {
    return css`
      min-width: var(--cds-spacing-09);
      ${$hasIncidents
        ? css`
            color: var(--cds-text-error);
          `
        : css`
            color: var(--cds-text-secondary);
          `}
    `;
  }}
`;

type ActiveCountProps = {
  $hasActiveInstances?: boolean;
};

const ActiveCount = styled.div<ActiveCountProps>`
  ${({$hasActiveInstances}) => {
    return css`
      margin-left: auto;
      width: 139px;
      text-align: right;

      color: ${$hasActiveInstances
        ? 'var(--cds-tag-color-green)'
        : 'var(--cds-text-primary)'};
    `;
  }}
`;

type LabelProps = {
  $isRed?: boolean;
  $size?: 'small' | 'medium';
};

const Label = styled.div<LabelProps>`
  ${({$size, $isRed}) => {
    return css`
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      ${styles.bodyCompact01}
      color: var(--cds-text-secondary);
      ${$size === 'medium' &&
      css`
        ${styles.headingCompact01}
        color: var(--cds-text-primary);
      `}
      ${$isRed &&
      css`
        color: var(--cds-text-error);
      `}
    `;
  }}
`;

const BarContainer = styled.div`
  position: relative;
  margin: var(--cds-spacing-03) 0;
`;

type ActiveBarProps = {
  $isPassive?: boolean;
  $size: Size;
};

const getBarStyles = ($size: Size) => {
  return css`
    ${$size === 'small' &&
    css`
      height: var(--cds-spacing-01);
    `}
    ${$size === 'medium' &&
    css`
      height: var(--cds-spacing-02);
    `}
    ${$size === 'large' &&
    css`
      height: var(--cds-spacing-03);
    `}
  `;
};

const ActiveInstancesBar = styled.div<ActiveBarProps>`
  ${({$isPassive, $size}) => {
    return css`
      ${getBarStyles($size)};
      background: ${$isPassive
        ? 'var(--cds-border-subtle-01)'
        : 'var(--cds-support-success)'};
    `;
  }}
`;

type IncidentsBarProps = {
  $size: Size;
};

const IncidentsBar = styled.div<IncidentsBarProps>`
  ${({$size}) => {
    return css`
      ${getBarStyles($size)};
      position: absolute;
      top: 0;
      background: var(--cds-support-error);
    `;
  }}
`;

export {
  Wrapper,
  IncidentsCount,
  ActiveCount,
  Label,
  BarContainer,
  ActiveInstancesBar,
  IncidentsBar,
};
