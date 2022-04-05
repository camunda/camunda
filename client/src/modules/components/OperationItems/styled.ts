/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

import {ReactComponent as Retry} from 'modules/components/Icon/retry.svg';
import {ReactComponent as Stop} from 'modules/components/Icon/stop.svg';
import {ReactComponent as Delete} from 'modules/components/Icon/delete.svg';

const iconStyle: ThemedInterpolationFunction = ({theme}) => {
  const colors = theme.colors.modules.operationItems.iconStyle;

  return css`
    opacity: 0.7;
    color: ${colors.color};
  `;
};

const RetryIcon = styled(Retry)`
  ${iconStyle};
`;

const CancelIcon = styled(Stop)`
  ${iconStyle};
`;

const DeleteIcon = styled(Delete)`
  ${iconStyle};
`;

const dynamicBorderStyles = (backgroundColor: any, borderColor: any) => css`
  /* change color around right border*/
  &:after {
    content: '';
    position: absolute;
    top: -1px;
    right: -1px;
    height: 100%;
    width: 2px;
    z-index: 1;
    background: ${backgroundColor};
    border-top: 1px solid ${borderColor};
    border-bottom: 1px solid ${borderColor};
  }

  /* change color of right border */
  & + :before {
    z-index: 2;
    background: ${borderColor};
  }
`;

const Ul = styled.ul`
  ${({theme}) => {
    const shadow = theme.shadows.modules.operationItems.ul;

    return css`
      display: inline-flex;
      flex-direction: row;
      border-radius: 12px;
      box-shadow: ${shadow};
    `;
  }}
`;

type LiProps = {
  disabled?: boolean;
};

const Li = styled.li<LiProps>`
  ${({theme, disabled}) => {
    const colors = theme.colors.modules.operationItems;

    return css`
      position: relative;
      padding: 1px;
      border: 1px solid ${colors.default.border};
      background: ${colors.default.background};
      cursor: ${disabled ? 'not-allowed' : 'pointer'};

      &:first-child&:last-child {
        border-radius: 12px;
        border-right-width: 1px;
        border-left-width: 1px;

        &:before,
        &:after {
          display: none;
        }
      }

      &:last-child {
        border-radius: 0 12px 12px 0;
        border-left-width: 0px;

        &:hover,
        &:active {
          border-left-width: 0;
        }

        /* creates custom left border */
        &:before {
          content: '';
          position: absolute;
          top: 2px;
          left: 0px;
          height: 20px;
          width: 1px;
          background: ${colors.default.border};
        }

        ${!disabled &&
        css`
          &:hover&:before {
            background: ${colors.hover.border};
          }

          &:active&:before {
            background: ${colors.active.border};
          }
        `}

        /* puts focus in same shape as element */
        > button {
          border-radius: inherit;
        }
      }

      &:first-child {
        border-radius: 12px 0 0 12px;
        border-right-width: 0;

        &:hover,
        &:active {
          border-right-width: 0;
        }

        ${!disabled &&
        css`
          &:hover {
            ${dynamicBorderStyles(colors.hover.background, colors.hover.border)}
          }
          &:active {
            ${dynamicBorderStyles(
              colors.active.background,
              colors.active.border
            )}
          }
        `}

        /* Puts focus in same shape as element */
        > button {
          border-radius: inherit;
        }
      }

      &:focus-within {
        z-index: 1;
        &:before,
        &:after {
          z-index: -1;
        }

        &:hover {
          &:after {
            z-index: -1;
          }
        }
      }

      ${!disabled &&
      css`
        &:hover {
          background: ${colors.hover.background};
          border: 1px solid ${colors.hover.border};
        }

        &:active {
          background: ${colors.active.background};
          border: 1px solid ${colors.active.border};
        }
      `}
    `;
  }}
`;

const Button = styled.button`
  display: flex;
  align-items: center;
  padding: 3px;
  background: none;
  border: none;
  border-radius: 12px;
  cursor: pointer;

  &:disabled {
    cursor: not-allowed;
    opacity: 0.4;
  }
`;

export {iconStyle, RetryIcon, CancelIcon, DeleteIcon, Ul, Li, Button};
