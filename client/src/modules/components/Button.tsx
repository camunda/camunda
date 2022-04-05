/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {rgba} from 'polished';

const primaryButtonStyles = css`
  height: 35px;
  padding: 8px;
  border-radius: 3px;
  font-size: 14px;
  font-weight: 600;
  box-shadow: ${({theme}) => theme.shadows.button.primary};
  border: solid 1px
    ${({theme}) => theme.colors.button.primary.default.borderColor};
  background-color: ${({theme}) =>
    theme.colors.button.primary.default.backgroundColor};
  color: ${({theme}) => theme.colors.ui04};

  &:hover {
    background-color: ${({theme}) =>
      theme.colors.button.primary.hover.backgroundColor};
    border-color: ${({theme}) => theme.colors.button.primary.hover.borderColor};
  }

  &:active {
    background-color: ${({theme}) =>
      theme.colors.button.primary.active.backgroundColor};
    border-color: ${({theme}) =>
      theme.colors.button.primary.active.borderColor};
  }

  &:disabled {
    background-color: ${({theme}) =>
      theme.colors.button.primary.disabled.backgroundColor};
    border-color: ${({theme}) =>
      theme.colors.button.primary.disabled.borderColor};
    color: ${({theme}) => rgba(theme.colors.ui04, 0.8)};
    cursor: not-allowed;
    box-shadow: none;
  }
`;

const smallButtonStyles = css`
  height: 22px;
  padding: 0 11px 0 9px;
  border-radius: 11px;
  border: solid 1px
    ${({theme}) => theme.colors.button.small.default.borderColor};
  background-color: ${({theme}) =>
    theme.colors.button.small.default.backgroundColor};

  font-size: 13px;
  color: ${({theme}) => theme.colors.text.black};

  &:hover {
    background-color: ${({theme}) =>
      theme.colors.button.small.hover.backgroundColor};
    border-color: ${({theme}) => theme.colors.button.small.hover.borderColor};
  }

  &:active {
    background-color: ${({theme}) =>
      theme.colors.button.small.active.backgroundColor};
    border-color: ${({theme}) => theme.colors.button.small.active.borderColor};
  }

  &:disabled {
    background-color: ${({theme}) =>
      theme.colors.button.small.disabled.backgroundColor};
    border-color: ${({theme}) =>
      theme.colors.button.small.disabled.borderColor};
    color: ${({theme}) => theme.colors.button.small.disabled.color};
    cursor: not-allowed;
  }
`;

interface Props {
  variant?: 'primary' | 'small';
}

const Button = styled.button<Props>`
  ${({variant}) => {
    switch (variant) {
      case 'small': {
        return smallButtonStyles;
      }
      case 'primary':
      default: {
        return primaryButtonStyles;
      }
    }
  }}
`;

export {Button};
