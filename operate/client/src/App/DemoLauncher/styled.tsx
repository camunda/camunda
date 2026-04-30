/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Button} from '@carbon/react';

const Container = styled.div`
  position: fixed;
  bottom: var(--cds-spacing-05);
  right: var(--cds-spacing-05);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-02);
  align-items: flex-end;
  pointer-events: none;
`;

const LauncherButton = styled(Button)`
  pointer-events: auto;
  opacity: 0.85;
  &:hover {
    opacity: 1;
  }
`;

export {Container, LauncherButton};
