/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Link} from 'react-router-dom';
import {Colors, themed, themeStyle} from 'modules/theme';
import withStrippedProps from 'modules/utils/withStrippedProps';

const METRIC_COLOR = {
  active: 'allIsWell',
  incidents: 'incidentsAndErrors'
};

export const Metric = themed(styled.div`
  display: inline-block;
  padding-top: 6px;
  padding-bottom: 16px;
  font-size: 56px;
  opacity: ${themeStyle({
    dark: 0.9,
    light: 1
  })};
  color: ${themeStyle({dark: '#ffffff', light: Colors.uiLight06})};
`);

export const MetricTile = themed(styled(
  withStrippedProps(['theme', 'type'])(Link)
)`
  display: flex;
  flex-direction: column;
  align-items: center;

  margin: 0 33px;

  border-radius: 3px;
  border: 1px solid transparent;

  &:hover {
    box-shadow: ${themeStyle({
      dark: '0 0 4px 0 #000000',
      light: '0 0 5px 0 rgba(0, 0, 0, 0.1)'
    })};
    border-color: ${themeStyle({
      dark: Colors.uiDark05,
      light: 'rgba(216, 220, 227, 0.5)'
    })};
  }

  &:active {
    box-shadow: ${themeStyle({
      dark: 'inset 0 0 6px 0 rgba(0, 0, 0, 0.4)',
      light: 'inset 0 0 6px 0 rgba(0, 0, 0, 0.1)'
    })};
    border-color: ${themeStyle({
      dark: 'rgba(91, 94, 99, 0.7)',
      light: 'rgba(216, 220, 227, 0.4)'
    })};
  }

  // Style child component based on type
  ${Metric.WrappedComponent} {
    color: ${({type}) => Colors[METRIC_COLOR[type]]};
    opacity: ${({type}) => type !== 'running' && 1};
  }
`);

export const Label = themed(styled.div`
  padding-bottom: 22px;

  opacity: ${themeStyle({
    dark: 0.9,
    light: 1
  })};

  font-size: 40px;
  line-height: 1.4;
  text-align: center;

  color: ${themeStyle({dark: '#ffffff', light: Colors.uiLight06})};
`);
