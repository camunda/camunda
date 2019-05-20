/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {ReactComponent as Right} from 'modules/components/Icon/right.svg';
import {themed, getExpandButtonTheme as getTheme} from 'modules/theme';
import {Transition as TransitionComponent} from 'modules/components/Transition';

export const ArrowIcon = styled(Right)`
  width: 16px;
  height: 16px;
  object-fit: contain;
`;

export const Icon = themed(styled.div`
  border-radius: 50%;
  border-color: none;
  height: 16px;
  width: 16px;
  z-index: 1;

  svg {
    // default arrow color/opacity
    ${props => getTheme(props.expandTheme).default.arrow[props.theme]}};
  }

  &:before {
    border-radius: 50%;
    position: absolute;
    content: '';
    height: 16px;
    width: 16px;
    z-index: -1;

    // default background color/opacity
    ${props => getTheme(props.expandTheme).default.background[props.theme]};
  }
`);

export const Button = themed(styled.button`
  display: flex;
  padding: 0;
  background: transparent;

  border-radius: 50%;

  &:hover {
    ${Icon.WrappedComponent}::before {
      // hover background color/opacity
      ${props => getTheme(props.expandTheme).hover.background[props.theme]}

      transition: background 0.15s ease-out;
    }

    ${Icon.WrappedComponent} {
      svg {
        // hover arrow color/opacity
        ${props => getTheme(props.expandTheme).hover.arrow[props.theme]};
      }
    }
  }

  &:active {
    ${Icon.WrappedComponent}::before {
      // active background color/opacity
      ${props => getTheme(props.expandTheme).active.background[props.theme]}
    }

    ${Icon.WrappedComponent} {
      svg {
        // active arrow color/opacity
        ${props => getTheme(props.expandTheme).active.arrow[props.theme]};
      }
    }
  }
`);

export const Transition = styled(TransitionComponent)`
  &.transition-enter {
    transform: none;
  }

  &.transition-enter-active {
    transform: rotate(90deg);
    ${props => `transition: transform ${props.timeout}ms`};
  }

  &.transition-enter-done {
    transform: rotate(90deg);
  }

  &.transition-exit {
    transform: rotate(90deg);
  }

  &.transition-exit-active {
    transform: none;
    ${props => `transition: transform ${props.timeout}ms`};
  }

  &.transition-exit-done {
    transform: none;
  }

  &.transition-appear {
    transform: rotate(90deg);
  }
`;
