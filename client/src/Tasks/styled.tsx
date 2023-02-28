/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import styled, {css} from 'styled-components';
import {Panel} from 'modules/components/Panel';
import {Stack} from '@carbon/react';

const Container = styled.main`
  display: flex;
  height: 100%;
  padding-top: ${rem(48)};
  box-sizing: border-box;
`;

const TasksPanel = styled(Stack)`
  ${() => css`
    align-content: flex-start;
    min-width: ${rem(300)};
    max-width: ${rem(300)};
    height: 100%;
  `}
`;

const DetailsPanel = styled(Panel)`
  border-left: 1px solid var(--cds-border-subtle);
  width: 100%;
  height: 100%;
`;

export {Container, TasksPanel, DetailsPanel};
