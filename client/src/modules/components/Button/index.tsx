/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

type Props = {
  size?: 'small' | 'medium' | 'large';
  color?: 'main' | 'primary' | 'secondary';
};

const getSizeVariant: ThemedInterpolationFunction<Props> = ({size}) => {
  switch (size) {
    case 'small':
      return css`
        height: 22px;
        font-size: 13px;
      `;
    case 'medium':
      return css`
        height: 35px;
        width: 117px;
        font-size: 14px;
      `;
    case 'large':
      return css`
        height: 48px;
        width: 340px;
        font-size: 18px;
      `;
    default:
      return undefined;
  }
};

const getColorVariant: ThemedInterpolationFunction<Props> = ({
  theme,
  color,
  size,
}) => {
  const isSmall = size === 'small';
  const colors = theme.colors.modules.button;
  const shadows = theme.shadows.modules.button;

  switch (color) {
    case 'secondary':
      return css`
        background-color: ${colors.secondary.backgroundColor};
        border: 1px solid ${colors.secondary.borderColor};
        color: ${colors.secondary.color};

        &:hover {
          background-color: ${colors.secondary.hover.backgroundColor};
          border-color: ${colors.secondary.hover.borderColor};
          color: ${colors.secondary.hover.color};
        }

        &:active {
          background-color: ${theme.colors.button06};
          border-color: ${colors.secondary.active.borderColor};
          color: ${colors.secondary.active.color};
        }

        &:disabled {
          background-color: ${colors.secondary.disabled.backgroundColor};
          color: ${colors.secondary.disabled.color};
        }
      `;
    case 'primary':
      return css`
        background-color: ${theme.colors.selections};
        border: 1px solid ${theme.colors.primaryButton03};
        color: ${colors.primary.color};
        ${isSmall
          ? ''
          : css`
              box-shadow: 0 2px 2px 0 rgba(${theme.colors.black}, 0.35);
            `}

        &:hover {
          background-color: ${theme.colors.primaryButton03};
          border-color: ${theme.colors.primaryButton04};
        }

        &:focus {
          box-shadow: ${shadows.primaryFocus};
        }

        &:active {
          background-color: ${theme.colors.primaryButton04};
          border-color: ${theme.colors.primaryButton05};
        }

        &:disabled {
          background-color: ${theme.colors.primaryButton02};
          border-color: ${theme.colors.primaryButton01};
          color: ${colors.primary.color};
          box-shadow: none;
        }
      `;
    case 'main':
    default:
      return css`
        color: ${colors.main.color};
        background-color: ${theme.colors.ui05};
        border: 1px solid ${colors.main.borderColor};

        &:hover {
          background-color: ${colors.main.hover.backgroundColor};
          border-color: ${colors.main.hover.borderColor};
        }

        &:focus {
          border-color: ${colors.main.focus.borderColor};
        }

        &:active {
          background-color: ${colors.main.active.backgroundColor};
          border-color: ${colors.main.active.borderColor};
        }

        &:disabled {
          cursor: not-allowed;
          background-color: ${colors.main.disabled.backgroundColor};
          border-color: ${colors.main.disabled.borderColor};
          color: ${colors.main.disabled.color};
          box-shadow: none;
        }
      `;
  }
};

const Button = styled.button<Props>`
  ${({theme, size}) => {
    const shadow = theme.shadows.modules.button.default;
    const isSmall = size === 'small';

    return css`
      font-family: IBM Plex Sans;
      font-weight: 600;
      border-radius: ${isSmall ? 11 : 3}px;
      ${isSmall ? '' : shadow};
      ${getSizeVariant}
      ${getColorVariant}
    `;
  }}
`;

export {Button};
