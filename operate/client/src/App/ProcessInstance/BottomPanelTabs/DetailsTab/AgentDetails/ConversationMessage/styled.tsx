/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
`;

const Preview = styled.div`
  max-height: 160px;
  overflow-y: auto;
  white-space: pre-wrap;
  font-size: var(--cds-body-compact-01-font-size);
  line-height: var(--cds-body-compact-01-line-height);
  color: var(--cds-text-secondary);
  padding: var(--cds-spacing-03);
  border: 1px solid var(--cds-border-subtle-01);
  border-radius: 2px;
`;

const Actions = styled.div`
  display: flex;
  gap: var(--cds-spacing-03);
  justify-content: flex-end;
`;

export {Container, Preview, Actions};
