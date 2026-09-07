/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Text} from '@camunda/design-system';
import styled from 'styled-components';

const Root = styled.section`
  display: flex;
  min-width: 0;
  max-height: 160px;
  flex: 1;
  flex-direction: column;
  gap: var(--cds-spacing-02);
  padding: var(--cds-spacing-02) 0;
`;

const Reasoning = styled(Text)`
  min-height: 0;
  margin: 0;
  overflow-x: hidden;
  overflow-y: auto;
  color: var(--neutral-foreground-subtle);
  font-size: var(--cds-label-01-font-size);
  font-style: italic;
  line-height: var(--cds-label-01-line-height);
  letter-spacing: var(--cds-label-01-letter-spacing);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
`;

export {Reasoning, Root};
