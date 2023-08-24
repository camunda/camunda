/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {Stack as BaseStack} from '@carbon/react';

const Container = styled.div`
  display: flex;
  width: 100%;
  justify-content: flex-end;
  padding: var(--cds-spacing-01) var(--cds-spacing-05);
`;
const Stack = styled(BaseStack)`
  align-items: center;
`;

export {Container, Stack};
