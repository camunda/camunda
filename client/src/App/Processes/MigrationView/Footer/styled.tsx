/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {Stack} from '@carbon/react';

const Container = styled(Stack)`
  background-color: var(--cds-layer);
  width: 100%;
  justify-content: flex-end;
  padding: var(--cds-spacing-03) var(--cds-spacing-05);
  border-top: 1px solid var(--cds-border-subtle-01);
`;

export {Container};
