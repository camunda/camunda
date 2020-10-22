/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import Menu from './Menu';

const MenuComponent = styled(Menu)`
  ${({transitionTiming}) => {
    return css`
      &.transition-enter {
        opacity: 0;
      }
      &.transition-enter-active {
        opacity: 1;
        transition: opacity ${transitionTiming.enter}ms ease-out;
      }
      &.transition-enter-done {
        opacity: 1;
      }
      &.transition-exit {
        opacity: 0;
        transition: opacity ${transitionTiming.exit}ms;
      }
    `;
  }}
`;

const Dropdown = styled.div`
  position: relative;
`;

const Button = styled.button`
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
      font-family: IBMPlexSans;
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
