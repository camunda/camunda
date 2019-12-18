/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {COMBO_BADGE_TYPE} from 'modules/constants';
import {Colors, themed, themeStyle} from 'modules/theme';

import Badge from '../Badge';

const selectionStyles = css`
  opacity: 1;
  color: ${themeStyle({
    dark: 'rgba(255,255,255, 0.8)',
    light: 'rgba(255,255,255, 0.9)'
  })};
  background-color: ${themeStyle({
    dark: '#437AD3',
    light: '#7FAEFC'
  })};
`;

const selectionsRightStyle = css`
  ${props =>
    props.isActive
      ? 'background-color: rgba(77, 144, 255, 0.75)'
      : selectionStyles};
`;

const selectionsLeftStyle = css`
  ${props =>
    props.isActive
      ? `background-color: ${Colors.selections}`
      : selectionStyles};
`;

export const ComboBadge = styled.div`
  position: relative;
  height: 21px;
  min-width: 48px;
  display: flex;
  align-items: center;
  margin-left: 6px;
`;

export const Left = themed(styled(Badge)`
  z-index: 4;
  min-width: 23px;
  height: 23px;
  border-radius: 45%;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-left: 0px;
  border-style: solid;
  border-width: 2px;
  border-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};
  ${props =>
    props.type === COMBO_BADGE_TYPE.SELECTIONS ? selectionsLeftStyle : ''};
`);

export const Right = themed(styled(Badge)`
  padding-left: 15px;
  padding-right: 10px;
  margin-left: -10px;
  ${props =>
    props.type === COMBO_BADGE_TYPE.SELECTIONS ? selectionsRightStyle : ''};
`);
