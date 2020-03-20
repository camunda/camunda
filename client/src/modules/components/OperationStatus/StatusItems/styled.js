/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed} from 'modules/theme';

import {ReactComponent as Retry} from 'modules/components/Icon/retry.svg';
import {ReactComponent as Stop} from 'modules/components/Icon/stop.svg';

export const Ul = themed(styled.ul`
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 0 0 2px;
  border-radius: 12px;
  background: transparent;
`);

export const Li = themed(styled.li`
  display: flex;
  align-items: center;
  padding: 3px;

  background: none;
  border: none;
  border-radius: 12px;
`);

export const iconStyle = css`
  color: ${Colors.incidentsAndErrors};
`;

// import and style any Icon accordingly
export const RetryIcon = themed(styled(Retry)`
  ${iconStyle};
`);

export const CancelIcon = themed(styled(Stop)`
  ${iconStyle};
`);
