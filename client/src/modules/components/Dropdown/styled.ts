/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import Menu from './Menu';

type Props = {
  $transitionTiming: Record<'enter' | 'exit', number>;
};

const MenuComponent = styled(Menu)<Props>`
  ${({$transitionTiming}) => {
    return css`
      &.transition-enter {
        opacity: 0;
      }
      &.transition-enter-active {
        opacity: 1;
        transition: opacity ${$transitionTiming.enter}ms ease-out;
      }
      &.transition-enter-done {
        opacity: 1;
      }
      &.transition-exit {
        opacity: 0;
        transition: opacity ${$transitionTiming.exit}ms;
      }
    `;
  }}
`;

const Dropdown = styled.div`
  position: relative;
`;

type ButtonProps = {
  disabled?: boolean;
  buttonStyles?: string;
};

const Button = styled.button<ButtonProps>`
  ${({theme, disabled, buttonStyles}) => {
    const colors = theme.colors.modules.dropdown.button;

    return css`
      position: relative;
      display: flex;
      align-items: center;
      border: none;
      padding-right: 0px;
      color: ${disabled ? colors.disabled.color : colors.default.color};
      background: none;
      font-family: IBM Plex Sans;
      font-size: 15px;
      font-weight: 600;
      cursor: ${disabled ? 'default' : 'pointer'};

      & > svg {
        vertical-align: text-bottom;
      }

      ${buttonStyles};
    `;
  }}
`;

const LabelWrapper = styled.div`
  margin-right: 8px;
`;

export {MenuComponent, Dropdown, Button, LabelWrapper};
