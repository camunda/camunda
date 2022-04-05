/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

import {SIZES, DEFAULT_SIZE} from './constants';

type IconButtonTheme = 'default' | 'foldable';

const setSize: ThemedInterpolationFunction<{size?: 'medium' | 'large'}> = ({
  size,
}) => {
  const length = size === undefined ? SIZES[DEFAULT_SIZE] : SIZES[size];

  return css`
    height: ${length}px;
    width: ${length}px;
  `;
};

type IconProps = {
  iconButtonTheme?: IconButtonTheme;
  size?: 'medium' | 'large';
};

const Icon = styled.div<IconProps>`
  ${({theme, iconButtonTheme}) => {
    const variant = iconButtonTheme ?? 'default';
    const colors = theme.colors.modules.iconButton;
    const opacity = theme.opacity.modules.iconButton;

    return css`
      border-radius: 50%;
      position: relative;
      z-index: 1;
      ${setSize}

      svg {
        color: ${colors.icon[variant].svg.color};
        opacity: ${opacity.icon[variant].svg};
      }

      &:before {
        border-radius: 50%;
        position: absolute;
        left: 0;
        content: '';
        z-index: -1;
        ${setSize}

        background-color: ${theme.colors.transparent};
      }
    `;
  }}
`;

type ButtonProps = {
  disabled?: boolean;
  iconButtonTheme?: IconButtonTheme;
};

const Button = styled.button<ButtonProps>`
  ${({theme, disabled, iconButtonTheme}) => {
    const variant = iconButtonTheme ?? 'default';
    const colors = theme.colors.modules.iconButton;
    const opacity = theme.opacity.modules.iconButton;

    return css`
      display: flex;
      padding: 0;
      background: transparent;
      border-radius: 50%;

      &:hover {
        ${Icon}::before {
          ${disabled
            ? ''
            : css`
                background-color: ${colors.button[variant].hover.before
                  .backgroundColor};
                opacity: ${opacity.button[variant].hover.before};
              `}

          transition: background 0.15s ease-out;
        }

        ${Icon} {
          svg {
            ${disabled
              ? ''
              : css`
                  color: ${colors.button[variant].hover.svg.color};
                  opacity: ${opacity.button[variant].hover.svg};
                `}
          }
        }
      }

      &:active {
        ${Icon}::before {
          background-color: ${colors.button[variant].active.before
            .backgroundColor};
          opacity: ${opacity.button[variant].active.before};
        }

        ${Icon} {
          svg {
            color: ${colors.button[variant].active.svg.color};
            opacity: ${opacity.button[variant].active.svg};
          }
        }
      }
    `;
  }}
`;

export {Icon, Button};
