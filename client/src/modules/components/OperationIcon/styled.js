/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {themed, themeStyle, Colors} from 'modules/theme';
import {ReactComponent as RetryOperation} from 'modules/components/Icon/retry.svg';
import {ReactComponent as CancelOperation} from 'modules/components/Icon/stop.svg';
import {ReactComponent as EditOperation} from 'modules/components/Icon/edit.svg';

export const OperationIcon = themed(styled.div`
  cursor: default;
  width: 16px;
  height: 16px;
  margin-top: 10px;
`);

const iconStyle = css`
  width: 16px;
  height: 16px;
  object-fit: contain;
  opacity: ${themeStyle({
    dark: 0.9,
    light: 0.8
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiDark02
  })};
`;

export const Retry = themed(styled(RetryOperation)`
  ${iconStyle};
`);

export const Cancel = themed(styled(CancelOperation)`
  ${iconStyle};
`);

export const Edit = themed(styled(EditOperation)`
  ${iconStyle};
`);
