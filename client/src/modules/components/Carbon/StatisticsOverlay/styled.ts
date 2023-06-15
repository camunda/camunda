/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {Stack} from '@carbon/react';

const staticColors = {
  completed: '#9ea9b7',
  canceled: '#88888d',
};

const backgroundColors = {
  light: {
    active: {
      color: 'var(--cds-support-success)',
      fadedColor: '#96baa8',
    },
    incidents: {
      color: 'var(--cds-support-error)',
      fadedColor: '#cc8a8a',
    },
    completed: {
      color: staticColors.completed,
      fadedColor: staticColors.completed,
    },
    canceled: {
      color: staticColors.canceled,
      fadedColor: staticColors.canceled,
    },
  },
  dark: {
    active: {
      color: 'var(--cds-support-success)',
      fadedColor: '#6f897d',
    },
    incidents: {
      color: 'var(--cds-support-error)',
      fadedColor: '#94595b',
    },
    completed: {
      color: staticColors.completed,
      fadedColor: staticColors.completed,
    },
    canceled: {
      color: staticColors.canceled,
      fadedColor: staticColors.canceled,
    },
  },
};

type StatisticProps = {
  $theme: 'dark' | 'light';
  $state: 'active' | 'incidents' | 'completed' | 'canceled';
  $isFaded: boolean;
};

const Statistic = styled(Stack)<StatisticProps>`
  ${({$theme, $state, $isFaded}) => {
    const backgroundColor =
      backgroundColors[$theme][$state][$isFaded ? 'fadedColor' : 'color'];

    return css`
      align-items: center;
      font-weight: bold;
      font-size: 13px;
      height: 24px;
      border-radius: 12px;
      transform: translateX(-50%);
      background-color: ${backgroundColor};
      color: white;
      padding: var(--cds-spacing-02) var(--cds-spacing-03) var(--cds-spacing-02)
        var(--cds-spacing-02);
    `;
  }}
`;

export {Statistic};
