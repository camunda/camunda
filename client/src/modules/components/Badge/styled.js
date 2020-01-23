/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {BADGE_TYPE} from 'modules/constants';
import {Colors, themed, themeStyle} from 'modules/theme';

const runningInstancesStyle = css`
  background-color: ${themeStyle({
    light: Colors.uiDark04,
    dark: Colors.uiLight05
  })};
  color: ${themeStyle({
    light: '#ffffff',
    dark: Colors.uiDark04
  })};
`;

const filtersStyle = css`
  background-color: ${Colors.filtersAndWarnings};
  color: ${Colors.uiDark02};
`;

const incidentsStyle = css`
  background-color: ${Colors.incidentsAndErrors};
  color: #ffffff;
`;

const badgeStyle = props => {
  switch (props.type) {
    case BADGE_TYPE.FILTERS:
      return filtersStyle;
    case BADGE_TYPE.INCIDENTS:
      return incidentsStyle;
    default:
      return runningInstancesStyle;
  }
};

const opacityStyle = props =>
  props.isActive
    ? ''
    : css`
        opacity: ${themeStyle({
          dark: '0.8',
          light: '0.7'
        })};
      `;

const badgeCss = css`
  display: inline-block;
  height: 17px;
  margin-left: 7px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  ${badgeStyle};
  ${opacityStyle};
`;

export const Badge = themed(styled.div`
  padding: 0 6px;
  min-width: 17px;

  border-radius: 8.5px;
  ${badgeCss};
`);

export const BadgeCircle = themed(styled.div`
  width: 17px;
  padding: 0;
  text-align: center;
  border-radius: 50%;
  ${badgeCss};
`);
