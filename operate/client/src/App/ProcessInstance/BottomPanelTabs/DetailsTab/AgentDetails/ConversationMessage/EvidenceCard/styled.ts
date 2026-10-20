/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';

const Card = styled.section`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-02);
  padding: var(--cds-spacing-03);
  border: 1px solid var(--cds-border-subtle-01);
  border-radius: 4px;
  background-color: var(--cds-layer-01);
`;

const CardTitle = styled.h5`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
  ${styles.headingCompact01};
`;

const Fields = styled.dl`
  display: grid;
  grid-template-columns: max-content 1fr;
  column-gap: var(--cds-spacing-04);
  row-gap: var(--cds-spacing-02);
  ${styles.bodyCompact01};
`;

const Label = styled.dt`
  color: var(--cds-text-secondary);
`;

const Value = styled.dd`
  overflow-wrap: anywhere;
  white-space: pre-wrap;
`;

const CodeValue = styled(Value)`
  ${styles.code01};
  max-height: 12rem;
  overflow: auto;
`;

export {Card, CardTitle, Fields, Label, Value, CodeValue};
