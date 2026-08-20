/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Stack as BaseStack, Layer as BaseLayer} from '@carbon/react';

const Container = styled.div`
  display: flex;
  width: 100%;
  justify-content: flex-end;
  padding: var(--cds-spacing-01) var(--cds-spacing-05);
`;

const Stack = styled(BaseStack)`
  align-items: center;
`;

const Layer = styled(BaseLayer)`
  margin-left: auto;
`;

export {Container, Stack, Layer};
