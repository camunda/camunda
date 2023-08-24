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
    FAILED: {
      color: 'var(--cds-support-error)',
      fadedColor: '#cc8a8a',
    },
    completed: {
      color: staticColors.completed,
      fadedColor: staticColors.completed,
    },
    EVALUATED: {
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
    FAILED: {
      color: 'var(--cds-support-error)',
      fadedColor: '#94595b',
    },
    completed: {
      color: staticColors.completed,
      fadedColor: staticColors.completed,
    },
    EVALUATED: {
      color: staticColors.completed,
      fadedColor: staticColors.completed,
    },
    canceled: {
      color: staticColors.canceled,
      fadedColor: staticColors.canceled,
    },
  },
};

type ContainerProps = {
  $theme: 'dark' | 'light';
  $state:
    | 'active'
    | 'incidents'
    | 'completed'
    | 'canceled'
    | 'EVALUATED'
    | 'FAILED';
  $isFaded: boolean;
  $showStatistic?: boolean;
};

const Container = styled(Stack)<ContainerProps>`
  ${({$theme, $state, $isFaded, $showStatistic}) => {
    const backgroundColor =
      backgroundColors[$theme][$state][$isFaded ? 'fadedColor' : 'color'];

    return css`
      align-items: center;
      font-weight: bold;
      font-size: 13px;
      height: 24px;
      border-radius: 12px;
      background-color: ${backgroundColor};
      color: white;
      padding: var(--cds-spacing-02);
      ${$showStatistic &&
      css`
        padding-right: var(--cds-spacing-03);
        transform: translateX(-50%);
      `}
    `;
  }}
`;

export {Container};
