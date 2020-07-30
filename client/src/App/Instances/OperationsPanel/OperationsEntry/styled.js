/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import {ReactComponent as RetryOperation} from 'modules/components/Icon/retry.svg';
import {ReactComponent as CancelOperation} from 'modules/components/Icon/stop.svg';
import {ReactComponent as EditOperation} from 'modules/components/Icon/edit.svg';

const runningEntryStyles = css`
  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight04,
  })};
`;

const finishedEntryStyles = css`
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight02,
  })};
`;

export const Entry = themed(styled.li`
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)',
  })};

  ${({isRunning}) => (isRunning ? runningEntryStyles : finishedEntryStyles)}

  border-top: ${themeStyle({
    dark: `solid 1px ${Colors.uiDark04}`,
    light: `solid 1px ${Colors.uiLight05}`,
  })};
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 130px;
  padding: 17px 27px 26px 27px;
`);

export const EntryStatus = themed(styled.div`
  display: flex;
  flex-direction: row;
  justify-content: space-between;
`);

export const Type = styled.div`
  font-size: 15px;
  line-height: 19px;
  font-weight: 600;

  margin-bottom: 6px;
`;

export const Id = styled.div`
  font-size: 11px;
`;

export const EntryDetails = styled.div`
  display: flex;
  flex-direction: row;
  justify-content: space-between;
`;

export const EndDate = styled.div`
  font-size: 14px;
`;

export const InstancesCount = themed(styled.div`
  font-size: 14px;
  color: ${themeStyle({
    dark: `${Colors.darkLinkDefault}`,
    light: `${Colors.lightLinkDefault}`,
  })};
  text-decoration: underline;

  cursor: pointer;
`);

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
    light: 0.8,
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiDark02,
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
