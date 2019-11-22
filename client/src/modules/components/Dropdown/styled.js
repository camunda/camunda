/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {themed, themeStyle} from 'modules/theme';
import Menu from './Menu';

const openDropdownTransitionStyle = css`
  &.transition-enter {
    opacity: 0;
  }
  &.transition-enter-active {
    opacity: 1;
    transition: opacity ${({transitionTiming}) => transitionTiming.enter + 'ms'}
      ease-out;
  }
  &.transition-enter-done {
    opacity: 1;
  }
  &.transition-exit {
    opacity: 0;
    transition: opacity ${({transitionTiming}) => transitionTiming.exit + 'ms'};
  }
`;

export const MenuComponent = styled(Menu)`
  ${openDropdownTransitionStyle};
`;

export const Dropdown = styled.div`
  position: relative;
`;

export const Button = themed(styled.button`
  /* Positioning */
  position: relative;
  display: flex;
  align-items: center;

  /* Display & Box Model */
  border: none;

  /* Color */
  color: ${({disabled}) =>
    disabled
      ? themeStyle({
          dark: 'rgba(255, 255, 255, 0.6)',
          light: 'rgba(98, 98, 110, 0.6);'
        })
      : themeStyle({
          dark: 'rgba(255, 255, 255, 0.9)',
          light: 'rgba(98, 98, 110, 0.9)'
        })};

  background: none;

  /* Text */
  font-family: IBMPlexSans;
  font-size: 15px;
  font-weight: 600;

  /* Other */
  cursor: ${({disabled}) => (disabled ? 'default' : 'pointer')};

  & > svg {
    vertical-align: text-bottom;
  }

  ${props => props.buttonStyles};
`);

export const LabelWrapper = styled.div`
  margin-right: 8px;
`;
