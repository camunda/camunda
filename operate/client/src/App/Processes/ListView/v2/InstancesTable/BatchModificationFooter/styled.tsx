/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack as BaseStack} from '@carbon/react';
import styled from 'styled-components';

const Stack = styled(BaseStack)`
  background-color: var(--cds-layer);
  width: 100%;
  justify-content: flex-end;
  padding: var(--cds-spacing-03) var(--cds-spacing-05);
  border-top: 1px solid var(--cds-border-subtle-01);
`;

export {Stack};
