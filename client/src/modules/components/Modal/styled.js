/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {ReactComponent as CloseLarge} from 'modules/components/Icon/close-large.svg';
import Panel from 'modules/components/Panel';
import Button from 'modules/components/Button';
import {Transition as TransitionComponent} from 'modules/components/Transition';

import {themed, Colors, themeStyle} from 'modules/theme';

export const ModalRoot = themed(styled.div`
  z-index: 999;
  position: absolute;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: ${themeStyle({
    dark: 'rgba(0, 0, 0, 0.5)',
    light: 'rgba(255, 255, 255, 0.7)'
  })};
`);

export const Transition = themed(styled(TransitionComponent)`
  &.transition-enter {
    opacity: 0;

    > div {
      transform: scale(0.9);
    }
  }
  &.transition-enter-active {
    opacity: 1;
    transition: opacity ${({timeout}) => timeout + 'ms'};

    > div {
      /* 'perspective' prevents a glitch in chrome which shifts content 1px after the transition ended */
      transform: scale(1) perspective(1px);
      transition: transform ${({timeout}) => timeout + 'ms'};
    }
  }

  &.transition-enter-done {
    opacity: 1;
    > div {
      transform: scale(1) perspective(1px);
    }
  }

  &.transition-exit {
    opacity: 1;
    > div {
      transform: scale(1) perspective(1px);
    }
  }

  &.transition-exit-active {
    opacity: 0;
    transition: opacity ${({timeout}) => timeout + 'ms'};
    > div {
      transform: scale(0.9) perspective(1px);
      transition: transform ${({timeout}) => timeout + 'ms'};
    }
  }
`);

export const ModalContent = themed(styled(Panel)`
  width: 80%;
  height: 90%;
  border: 1px solid
    ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05
    })};
  border-radius: 3px;
  box-shadow: 0 2px 2px 0
    ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.5)',
      light: 'rgba(0, 0, 0, 0.5)'
    })};
`);

export const ModalHeader = themed(styled(Panel.Header)`
  height: 55px;
  padding-top: 18px;
  padding-bottom: 19px;
  padding-left: 20px;
  border-bottom: 1px solid
    ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05
    })};
  border-radius: 3px 3px 0 0;
`);

export const CrossButton = themed(styled.button`
  padding: 0;
  margin: 0;
  background: transparent;
  border: 0;
  position: absolute;
  right: 21px;
  top: 19px;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};

  opacity: ${themeStyle({
    dark: 0.5,
    light: 0.9
  })};

  &:hover {
    opacity: ${themeStyle({
      dark: 0.7,
      light: 1
    })};
  }

  &:active {
    color: ${themeStyle({
      dark: '#ffffff',
      light: Colors.uiDark04
    })};
    opacity: 1;
  }

  &:focus {
    opacity: 1;
  }
`);

export const CrossIcon = themed(styled(CloseLarge)`

  }
`);

export const ModalBody = themed(styled(Panel.Body)`
  padding: 14px 29px 14px 19px;

  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};

  background-color: ${themeStyle({
    dark: Colors.uiDark01,
    light: Colors.uiLight04
  })};
`);

export const ModalBodyText = themed(styled.div`
  font-size: 13px
  opacity: ${themeStyle({
    dark: '0.9',
    light: '1'
  })}
`);

export const ModalFooter = themed(styled(Panel.Footer)`
  height: 63px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04
  })};
  border-top: 1px solid
    ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05
    })};
  border-radius: 0 0 3px 3px;

  & > button {
    margin-left: 15px;
  }
`);

export const CloseButton = styled(Button)`
  background-color: ${Colors.selections};
  color: ${Colors.uiLight02};
`;
