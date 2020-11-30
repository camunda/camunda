/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import styled from 'styled-components';
import {Panel} from 'modules/components/Panel';
import {CollapsablePanel} from 'modules/components/CollapsablePanel';

const TasksPanel = styled(CollapsablePanel)`
  height: 100%;
  background-color: ${({theme}) => theme.colors.ui02};
`;

const Container = styled.main`
  display: flex;
  height: calc(100% - 56px);

  & > ${TasksPanel} {
    margin-right: 1px;
  }
`;

const DetailsPanel = styled(Panel)`
  border: 1px solid ${({theme}) => theme.colors.ui05};
  width: 100%;
  height: 100%;
`;

const NoTaskSelectedMessage = styled.h1`
  font-size: 16px;
  color: ${({theme}) => theme.colors.text.black};
  text-align: center;
  padding-top: 40px;
  font-weight: normal;
`;

export {Container, TasksPanel, DetailsPanel, NoTaskSelectedMessage};
