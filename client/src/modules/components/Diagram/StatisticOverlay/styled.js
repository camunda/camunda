/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {themed, Colors} from 'modules/theme';

export const StatisticSpan = styled.span`
  padding: 0 11px 0 0px;
`;

const statisticBackground = ({state, theme}) => {
  const background = {
    active: {light: Colors.allIsWell, dark: Colors.allIsWell},
    incidents: {
      light: Colors.incidentsAndErrors,
      dark: Colors.incidentsAndErrors,
    },
    completed: {
      light: Colors.badge01,
      dark: Colors.badge02,
    },
    canceled: {
      light: Colors.badge02,
      dark: Colors.badge01,
    },
  };

  return background[state][theme];
};

const statisticColor = ({state, theme}) => {
  if (state === 'completed' && theme === 'light') {
    return Colors.uiLight06;
  }

  if (state === 'canceled' && theme === 'dark') {
    return Colors.uiDark04;
  }

  return '#ffffff';
};

export const Statistic = themed(styled.div`
  display: flex;
  line-height: 24px;
  height: 24px;
  font-family: IBMPlexSans;
  font-size: 13px;
  font-weight: bold;
  border-radius: 12px;
  transform: translateX(-50%);
  background-color: ${statisticBackground};
  color: ${statisticColor};
`);
