/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {themed, themeStyle, Colors, Animations} from 'modules/theme';

import {Transition as TransitionComponent} from 'modules/components/Transition';

import ContextualMessage from 'modules/components/ContextualMessage';

const themedWith = (dark, light) => {
  return themeStyle({
    dark,
    light
  });
};

export const Transition = themed(styled(TransitionComponent)`
  &.transition-enter {
    opacity: 0;
  }
  &.transition-enter-active {
    opacity: 1;
    transition: opacity ${({timeout}) => timeout + 'ms'};
    overflow: hidden;
    animation-name: ${Animations.fold(0, 474)};
    animation-duration: ${({timeout}) => timeout + 'ms'};
  }
  &.transition-enter-done {
    opacity: 1;
    transition: opacity ${({timeout}) => timeout + 'ms'};
  }
  &.transition-exit {
    opacity: 0;
    transition: opacity ${({timeout}) => timeout + 'ms'};
  }
  &.transition-exit-active {
    opacity: 0;
    max-height: 0px;
    overflow: hidden;
    animation-name: ${Animations.fold(474, 0)};
    animation-duration: ${({timeout}) => timeout + 'ms'};
  }
  &.transition-exit-done {
    opacity: 0;
    max-height: 0px;
  }
`);

export const Ul = styled.ul`
  padding-left: 35px;
  margin: 0px;
  overflow: auto;
  overflow-x: hidden;
`;

export const Li = styled.li`
  margin: 15px 0;

  &:first-child {
    margin-top: 20px;
  }
`;

export const MessageWrapper = styled.div`
  display: flex;
  justify-content: flex-end;
  /* margin-top: 20px; */
  padding-right: 40px;
`;

export const SelectionMessage = styled(ContextualMessage)`
  margin-top: 20px;
  height: 23px;
`;

export const NoSelectionWrapper = themed(styled.div`
  width: 443px;
  height: 94px;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-top: 20px;

  color: ${themedWith('#ffffff', Colors.uiLight06)};
  opacity: ${themedWith(0.9, 1)};
  background: ${themedWith(Colors.uiDark03, Colors.uiLight02)};
  border: 1px solid ${themedWith(Colors.uiDark04, Colors.uiLight05)};
  border-radius: 3px;

  text-align: center;
  font-size: 13px;
`);
