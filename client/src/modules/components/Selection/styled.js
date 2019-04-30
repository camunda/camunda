/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {themed, themeStyle, Colors, Animations} from 'modules/theme';
import {Transition as TransitionComponent} from 'modules/components/Transition';

import ActionStatus from 'modules/components/ActionStatus';

import {ReactComponent as RemoveItem} from 'modules/components/Icon/remove-item.svg';
import {ReactComponent as Retry} from 'modules/components/Icon/retry.svg';
import {ReactComponent as Batch} from 'modules/components/Icon/batch.svg';
import {ReactComponent as Stop} from 'modules/components/Icon/stop.svg';
import BadgeComponent from 'modules/components/Badge';

const themedWith = (dark, light) => {
  return themeStyle({
    dark,
    light
  });
};

export const iconStyle = css`
  position: relative;
  top: 3px;
  margin-right: 10px;
`;

export const Dl = styled.dl`
  margin: 0px;
  width: 442px;
`;

export const Ul = styled.ul`
  display: flex;
  flex-direction: column-reverse;
`;

export const OpenSelectionTransition = themed(styled(TransitionComponent)`
  &.transition-enter {
    opacity: 0;
  }
  &.transition-enter-active {
    opacity: 1;
    transition: opacity ${({timeout}) => timeout.enter + 'ms'};
    overflow: hidden;
    animation-name: ${Animations.fold(0, 474)};
    animation-duration: ${({timeout}) => timeout.enter + 'ms'};
  }

  &.transition-exit {
    opacity: 0;
    transition: opacity ${({timeout}) => timeout.exit + 'ms'};
  }
  &.transition-exit-active {
    opacity: 0;
    max-height: 0px;
    overflow: hidden;
    animation-name: ${Animations.fold(474, 0)};
    animation-duration: ${({timeout}) => timeout.exit + 'ms'};
  }
`);

export const AddInstanceTransition = themed(styled(TransitionComponent)`
  &.transition-enter {
    opacity: 0;
  }
  &.transition-enter-active {
    opacity: 1;
    transition: opacity ${({timeout}) => timeout + 'ms'};
  }
  &.transition-enter-done {
    opacity: 1;
  }
`);

export const Dd = styled.dd`
  padding: 0;
  margin: 0;
`;

const hoverSelector = () => {
  return css`
    &:hover {
      background: ${themedWith(Colors.uiDark04, Colors.lightButton04)};
      border-color: ${themedWith(Colors.uiDark06, Colors.uiLight03)};
      transition: background 0.15s ease-out, border-color 0.15s ease-out;
    }
  `;
};

export const Dt = themed(styled.dt`
  height: 32px;
  padding: 0;
  display: flex;
  align-items: center;
  position: relative;

  ${({isOpen}) => !isOpen && hoverSelector}

  background: ${({isOpen}) =>
    isOpen ? Colors.selections : themedWith(Colors.uiDark03, Colors.uiLight02)};
  border-style: solid;
  border-width: 1px 0 1px 1px;
  border-color: ${({isOpen}) =>
    isOpen ? '#659fff' : themedWith(Colors.uiDark05, Colors.uiLight05)};
  border-radius: 3px 0 0 ${({isOpen}) => (isOpen ? 0 : '3px')};
`);

export const Heading = themed(styled.span`
  display: flex;
  align-items: flex-start;
`);

export const SelectionToggle = themed(styled.button`
  /* Positioning */
  background: transparent;
  position: absolute;
  width: 100%;
  bottom: -1px;
  left: 0px;

  /* Color */
  color: ${({isOpen}) =>
    isOpen ? '#ffffff' : themedWith('#ffffff', Colors.uiLight06)};

  /* Display & Box Model */
  display: flex;
  align-items: center;
  border-radius: 3px 0 0 ${({isOpen}) => (isOpen ? 0 : '3px')};
  height: 32px;
  padding-left: 9px;
  padding-right: 21px;

  /* Text */
  font-size: 15px;
  font-weight: 600;

  /* Other */
  cursor: ${({isOpen}) => (!isOpen ? 'pointer' : '')};
`);

export const Headline = styled.div`
  padding-left: 25px;
  line-height: 16px;
`;

export const Actions = styled.div`
  display: flex;
  align-items: center;
  margin-left: auto;
  margin-right: 24px;
`;

export const DropdownWrapper = styled.div`
  display: flex;
  align-items: center;
  position: relative;
  top: 1px;
  height: 26px;
  z-index: 1;
  padding: 0 2px;
  margin-right: 10px;
  border-right: 1px solid ${'rgba(255, 255, 255, 0.15)'};
`;

export const ActionButton = styled.button`
  background: transparent;
  border-radius: 12px;
  width: 16px;
  height: 16px;
  padding: 0px;
  z-index: 3;
`;

export const DeleteIcon = styled(RemoveItem)`
  opacity: 0.45;
  cursor: pointer;
`;

export const ArrowIcon = themed(styled.div`
  position: relative;
  top: 1px;
  padding-left: 10px;
  cursor: pointer;

  color: ${({isOpen}) =>
    isOpen ? '#ffffff' : themedWith('#ffffff', Colors.uiLight06)};
`);

export const Badge = styled(BadgeComponent)`
  ${props =>
    !props.isOpen
      ? 'color: #ffffff'
      : `background: #ffffff; color: ${Colors.selections}`};
`;

export const RetryIcon = styled(Retry)`
  ${iconStyle};
`;

export const CancelIcon = styled(Stop)`
  ${iconStyle};
`;

export const BatchIcon = styled(Batch)`
  color: '#ffffff';
`;

export const OptionLabel = styled.label`
  margin-left: 8px;
`;

export const Li = themed(styled.li`
  display: flex;
  align-items: center;
  height: 31px;

  color: ${themedWith('#ffffff', Colors.uiDark04)};
  font-size: 13px;
  & * {
    top: 1px;
  }

  & > div {
    padding-left: 10px;
  }

  &:nth-child(even) {
    background: ${themedWith(
      Colors.darkInstanceEven,
      Colors.lightInstanceEven
    )};

    border-bottom: 1px solid ${themedWith(Colors.uiDark02, Colors.uiLight05)};
  }

  &:nth-child(odd) {
    background: ${themedWith(Colors.darkInstanceOdd, Colors.lightInstanceOdd)};
    border-bottom: 1px solid ${themedWith(Colors.uiDark02, Colors.uiLight05)};
  }
`);

export const StatusCell = styled.div`
  width: 25px;
`;

export const NameCell = styled.div`
  width: 50%;
`;

export const IdCell = styled.div`
  width: 50%;
`;

export const ActionStatusCell = styled.div`
  width: 185px;
`;

export const InstanceActionStatus = styled(ActionStatus)`
  margin-right: 97px;
`;

export const Footer = styled.footer`
  display: flex;
  justify-content: flex-end;
  align-items: center;
  height: 32px;
  padding: 8px 21px 7px 0;
  color: #ffffff;
  background: ${Colors.selections};
  border-radius: 0 0 0 3px;
  border-width: 1px 0 1px 1px;
  border-style: solid;
  border-color: #659fff;
`;

export const MoreInstances = styled.div`
  font-size: 13px;
`;

export const dropDownButtonStyles = css`
  color: #fff;
`;
