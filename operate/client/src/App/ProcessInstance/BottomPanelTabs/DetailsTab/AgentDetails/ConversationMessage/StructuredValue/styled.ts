/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';

const Fields = styled.dl`
  display: grid;
  grid-template-columns: minmax(max-content, 12rem) 1fr;
  column-gap: var(--cds-spacing-05);
  row-gap: var(--cds-spacing-03);
  margin: 0;
  ${styles.bodyCompact01};
`;

const Label = styled.dt`
  color: var(--cds-text-secondary);
`;

const Value = styled.dd`
  margin: 0;
  min-width: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  color: var(--cds-text-primary);
`;

const CodeText = styled.pre`
  margin: 0;
  padding: var(--cds-spacing-03);
  border-radius: 4px;
  background-color: var(--cds-layer-accent-01);
  ${styles.code01};
  max-height: 16rem;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
`;

const Items = styled.ol`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
  margin: 0;
  padding: 0;
  list-style: none;
`;

const Item = styled.li`
  padding: var(--cds-spacing-03);
  border: 1px solid var(--cds-border-subtle-01);
  border-radius: 4px;
  background-color: var(--cds-layer-01);
`;

const Container = styled.div`
  flex: 1;
  min-width: 0;
  overflow: auto;
`;

const Segments = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-04);
`;

export {Segments, Fields, Label, Value, CodeText, Items, Item, Container};
