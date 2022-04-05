/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
`;

const DetailsPanel = styled(Panel)`
  border: 1px solid ${({theme}) => theme.colors.ui05};
  border-top-width: 0;

  width: 100%;
  height: 100%;
`;

export {Container, TasksPanel, DetailsPanel};
