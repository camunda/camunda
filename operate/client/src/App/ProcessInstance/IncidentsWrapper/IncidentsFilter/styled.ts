/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Stack as BaseStack} from '@carbon/react';

const Container = styled.div`
  display: flex;
  width: 100%;
  flex-direction: column;
  padding: var(--cds-spacing-05) var(--cds-spacing-05) var(--cds-spacing-03);
`;

const Stack = styled(BaseStack)`
  width: 100%;
`;

export {Container, Stack};
