/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';

const Container = styled.div`
  display: flex;
  justify-content: center;
  ${styles.helperText01};
  background-color: var(--cds-notification-background-warning);
  border: 1px solid var(--cds-support-warning);
  box-shadow: 0 2px 6px var(--cds-shadow);
`;

const Text = styled.div`
  padding: var(--cds-spacing-03);
`;

export {Container, Text};
