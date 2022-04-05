/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as ClockIcon} from 'modules/components/Icon/clock.svg';

const Clock = styled(ClockIcon)`
  margin-right: 4px;
`;

const Count = styled.span`
  ${({theme}) => {
    return css`
      height: 16px;
      min-width: 21px;
      padding: 0px 5px;
      margin-left: 9px;
      border-radius: 8px;
      line-height: 16px;
      text-align: center;
      color: ${theme.colors.white};
    `;
  }}
`;

type PillProps = {
  grow?: boolean;
  variant?: 'FILTER' | 'TIMESTAMP';
  isActive?: boolean;
};

const Pill = styled.button<PillProps>`
  ${({theme, grow, variant, isActive}) => {
    const colors = theme.colors.modules.pill;
    const opacity = theme.opacity.modules.pill;

    return css`
      display: flex;
      align-items: center;
      ${grow
        ? css`
            width: 100%;
          `
        : ''}

      border-radius: 16px;
      font-size: 13px;
      padding: ${variant === 'FILTER' ? '3px 3px 3px 10px' : '3px 10px'};
      color: ${isActive ? theme.colors.white : colors.default.color};
      border-style: solid;
      border-width: 1px;
      border-color: ${isActive
        ? colors.active.borderColor
        : colors.default.borderColor};
      background: ${isActive
        ? colors.active.backgroundColor
        : colors.default.backgroundColor};

      ${Count} {
        background-color: ${isActive
          ? colors.count.active.backgroundColor
          : colors.count.default.backgroundColor};
        opacity: ${isActive ? opacity.active : opacity.default};
        ${isActive
          ? css`
              color: ${theme.colors.selections};
            `
          : ''}
      }

      &:disabled {
        border: solid 1px ${colors.disabled.borderColor};
        background-color: ${colors.disabled.backgroundColor};
        color: ${colors.disabled.color};
      }

      &:hover {
        ${isActive
          ? ''
          : css`
              background: ${colors.hover.backgroundColor};
            `}
        border-color: ${theme.colors.button02};

        &:hover ${Count} {
          background-color: ${colors.count.hover.backgroundColor};
          opacity: ${isActive ? opacity.count.active : opacity.count.default};
          ${isActive
            ? css`
                background-color: ${theme.colors.white};
                color: ${theme.colors.selections};
              `
            : ''}
        }
      }
    `;
  }}
`;

type LabelProps = {
  grow?: boolean;
};

const Label = styled.span<LabelProps>`
  ${({grow}) => {
    if (grow) {
      return css`
        flex-grow: 1;
        text-align: left;
        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
      `;
    }
  }}
`;

export {Clock, Count, Pill, Label};
