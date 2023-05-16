/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {rgba} from 'polished';

import {ReactComponent as CloseLarge} from 'modules/components/Icon/close-large.svg';
import {Panel} from 'modules/components/Panel';
import {Button} from 'modules/components/Button';
import {Transition as TransitionComponent} from 'modules/components/Transition';
import {zModal} from 'modules/constants/componentHierarchy';
import {SIZES} from './constants';
import {styles} from '@carbon/elements';

const ModalRoot = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.modal.modalRoot;

    return css`
      z-index: ${zModal};
      position: absolute;
      top: 0;
      left: 0;
      width: 100vw;
      height: 100vh;
      display: flex;
      justify-content: center;
      align-items: center;
      background-color: ${colors.backgroundColor};
    `;
  }}
`;

type TransitionProps = {
  timeout: number;
};

const Transition = styled(TransitionComponent)<TransitionProps>`
  ${({timeout}) => {
    return css`
      &.transition-enter {
        opacity: 0;

        > div {
          transform: scale(0.9);
        }
      }
      &.transition-enter-active {
        opacity: 1;
        transition: opacity ${timeout}ms;

        > div {
          /* 'perspective' prevents a glitch in chrome which shifts content 1px after the transition ended */
          transform: scale(1) perspective(1px);
          transition: transform ${timeout}ms;
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
        transition: opacity ${timeout}ms;
        > div {
          transform: scale(0.9) perspective(1px);
          transition: transform ${timeout}ms;
        }
      }
    `;
  }}
`;

const MODAL_SIZES = Object.freeze({
  [SIZES.BIG]: css`
    width: 80%;
    height: 90%;
  `,
  [SIZES.SMALL]: css`
    width: 636px;
    height: 325px;
  `,
});

type ModalContentProps = {
  size: 'BIG' | 'SMALL' | 'CUSTOM';
  $width?: string;
  $maxHeight?: string;
};

const ModalContent = styled(Panel)<ModalContentProps>`
  ${({theme, size, $width, $maxHeight}) => {
    const colors = theme.colors.modules.modal.modalContent;

    return css`
      ${size !== 'CUSTOM' &&
      css`
        ${MODAL_SIZES[size]}
      `}
      ${size === 'CUSTOM' &&
      css`
        ${$width &&
        css`
          width: ${$width};
        `};
        ${$maxHeight &&
        css`
          max-height: ${$maxHeight};
        `};
      `}
      border: 1px solid ${colors.borderColor};
      border-radius: 3px;
      box-shadow: 0 2px 2px 0 ${rgba(theme.colors.black, 0.5)};
    `;
  }}
`;

const ModalHeader = styled(Panel.Header)`
  ${({theme}) => {
    const colors = theme.colors.modules.modal.modalHeader;

    return css`
      height: 55px;
      padding-top: 18px;
      padding-bottom: 19px;
      padding-left: 20px;
      border-bottom: 1px solid ${colors.borderColor};
      border-radius: 3px 3px 0 0;
    `;
  }}
`;

const CrossButton = styled.button`
  ${({theme}) => {
    const colors = theme.colors.modules.modal.crossButton;
    const opacity = theme.opacity.modules.modal.crossButton;

    return css`
      padding: 0;
      margin: 0;
      background: transparent;
      border: 0;
      position: absolute;
      right: 21px;
      top: 19px;
      color: ${theme.colors.text02};
      opacity: ${opacity.default};

      &:hover {
        opacity: ${opacity.hover};
      }

      &:active {
        color: ${colors.active.color};
        opacity: 1;
      }

      &:focus {
        opacity: 1;
      }
    `;
  }}
`;

const CrossIcon = styled(CloseLarge)``;

const ModalBody = styled(Panel.Body)`
  ${({theme}) => {
    const colors = theme.colors.modules.modal.modalBody;

    return css`
      padding: 14px 29px 14px 19px;
      color: ${theme.colors.text02};
      background-color: ${colors.backgroundColor};
    `;
  }}
`;

const ModalBodyText = styled.div`
  ${({theme}) => {
    const opacity = theme.opacity.modules.modal.modalBodyText;

    return css`
      ${styles.bodyShort01};
      opacity: ${opacity};
    `;
  }}
`;

const ModalFooter = styled(Panel.Footer)`
  ${({theme}) => {
    const colors = theme.colors.modules.modal.modalFooter;

    return css`
      height: 63px;
      min-height: 63px;
      display: flex;
      justify-content: flex-end;
      align-items: center;
      background-color: ${colors.backgroundColor};
      border-top: 1px solid ${colors.borderColor};
      border-radius: 0 0 3px 3px;

      & > button {
        margin-left: 15px;
      }
    `;
  }}
`;

const CloseButton = styled(Button)`
  ${({theme}) => {
    const colors = theme.colors.modules.modal.closeButton;

    return css`
      background-color: ${theme.colors.selections};
      color: ${colors.color};
    `;
  }}
`;

export {
  ModalRoot,
  Transition,
  ModalContent,
  ModalHeader,
  CrossButton,
  CrossIcon,
  ModalBody,
  ModalBodyText,
  ModalFooter,
  CloseButton,
};
