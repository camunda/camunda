/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import Panel from 'modules/components/Panel';
import CollapseButton from 'modules/components/CollapseButton';
import {EXPAND_STATE} from 'modules/constants';
import withStrippedProps from 'modules/utils/withStrippedProps';

const isCollapsed = expandState => expandState === EXPAND_STATE.COLLAPSED;

export const Pane = styled(
  withStrippedProps([
    'onAddToOpenSelection',
    'onAddNewSelection',
    'onAddToSelectionById',
    'onSelectedInstancesUpdate',
    'onTreeRowSelection',
    'onFlowNodeSelection'
  ])(Panel)
)`
  ${({expandState}) => (isCollapsed(expandState) ? '' : `flex-grow: 1;`)};
`;

const collapsedStyle = css`
  display: none;
`;

export const Body = styled(Panel.Body)`
  ${({expandState}) => (isCollapsed(expandState) ? collapsedStyle : '')};
`;

export const Footer = Panel.Footer;

export const PaneCollapseButton = styled(CollapseButton)`
  margin: 0;
  margin-top: 3px;
  border-top: none;
  border-bottom: none;
  border-right: none;
`;

export const ButtonsContainer = styled.div`
  position: absolute;
  top: 0;
  right: ${({isShifted}) => (isShifted ? '422px' : 0)};
  display: flex;
  z-index: 2;
  transition: right 0.2s ease-out;
`;
