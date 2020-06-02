/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Panel} from './Panel';

const Container = styled.main`
  display: flex;
  height: calc(100% - 56px);
`;

const TasksPanel = styled(Panel)`
  margin-right: 1px;
  width: 480px;
`;

const DetailsPanel = styled(Panel)`
  width: 100%;
`;

const NoTaskSelectedMessage = styled.h1`
  font-size: 16px;
  color: ${({theme}) => theme.colors.text.black};
  text-align: center;
  padding-top: 40px;
  font-weight: normal;
`;

export {Container, TasksPanel, DetailsPanel, NoTaskSelectedMessage};
