/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import styled, {css} from 'styled-components';
import {Panel} from './Panel';
import {CollapsablePanel} from './CollapsablePanel';

const Container = styled.main`
  display: flex;
  height: calc(100% - 56px);
`;

const TasksPanel = styled(CollapsablePanel)`
  height: 100%;
  border-top-right-radius: 3px;
  margin-right: 1px;
  background-color: ${({theme}) => theme.colors.ui02};
`;

const panelBodyStyles = css`
  display: grid;
  grid-template-rows: auto 1fr;
  overflow: hidden;
`;

const DetailsPanel = styled(Panel)`
  border: 1px solid ${({theme}) => theme.colors.ui05};
  width: 100%;
`;

const NoTaskSelectedMessage = styled.h1`
  font-size: 16px;
  color: ${({theme}) => theme.colors.text.black};
  text-align: center;
  padding-top: 40px;
  font-weight: normal;
`;

export {
  Container,
  TasksPanel,
  DetailsPanel,
  NoTaskSelectedMessage,
  panelBodyStyles,
};
