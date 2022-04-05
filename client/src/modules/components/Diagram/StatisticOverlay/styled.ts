/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const StatisticSpan = styled.span`
  padding: 0 11px 0 0px;
`;

type StatisticProps = {
  state: 'active' | 'incidents' | 'completed' | 'canceled';
};

const Statistic = styled.div<StatisticProps>`
  ${({theme, state}) => {
    const colors =
      theme.colors.modules.diagram.statisticOverlay.statistic[state];

    return css`
      display: flex;
      line-height: 24px;
      height: 24px;
      font-family: IBM Plex Sans;
      font-size: 13px;
      font-weight: bold;
      border-radius: 12px;
      transform: translateX(-50%);
      background-color: ${colors.backgroundColor};
      color: ${theme.colors.white};
    `;
  }}
`;

export {StatisticSpan, Statistic};
