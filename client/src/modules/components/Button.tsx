/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const primaryButtonStyles = css`
  height: 35px;
  padding: 0 11px;
  border-radius: 3px;
  box-shadow: ${({theme}) => theme.shadows.primaryButton};
  border: solid 1px
    ${({theme}) => theme.colors.primaryButton.default.borderColor};
  background-color: ${({theme}) =>
    theme.colors.primaryButton.default.backgroundColor};

  font-size: 14px;
  color: ${({theme}) => theme.colors.ui04};

  &:hover {
    background-color: ${({theme}) =>
      theme.colors.primaryButton.hover.backgroundColor};
    border-color: ${({theme}) => theme.colors.primaryButton.hover.borderColor};
  }

  &:active {
    background-color: ${({theme}) =>
      theme.colors.primaryButton.active.backgroundColor};
    border-color: ${({theme}) => theme.colors.primaryButton.active.borderColor};
  }

  &:disabled {
    background-color: ${({theme}) =>
      theme.colors.primaryButton.disabled.backgroundColor};
    border-color: ${({theme}) =>
      theme.colors.primaryButton.disabled.borderColor};
    cursor: not-allowed;
  }
`;

const smallButtonStyles = css`
  height: 22px;
  padding: 0 11px;
  border-radius: 11px;
  border: solid 1px ${({theme}) => theme.colors.smallButton.default.borderColor};
  background-color: ${({theme}) =>
    theme.colors.smallButton.default.backgroundColor};

  font-size: 13px;
  color: ${({theme}) => theme.colors.text.black};

  &:hover {
    background-color: ${({theme}) =>
      theme.colors.smallButton.hover.backgroundColor};
    border-color: ${({theme}) => theme.colors.smallButton.hover.borderColor};
  }

  &:active {
    background-color: ${({theme}) =>
      theme.colors.smallButton.active.backgroundColor};
    border-color: ${({theme}) => theme.colors.smallButton.active.borderColor};
  }

  &:disabled {
    background-color: ${({theme}) =>
      theme.colors.smallButton.disabled.backgroundColor};
    border-color: ${({theme}) => theme.colors.smallButton.disabled.borderColor};
    color: ${({theme}) => theme.colors.smallButton.disabled.color};
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
