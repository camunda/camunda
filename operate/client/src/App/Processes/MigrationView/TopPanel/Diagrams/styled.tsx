/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Stack} from '@carbon/react';
import {styles} from '@carbon/elements';

const DiagramContainer = styled.div`
  display: flex;
  flex-grow: 1;
  .custom-gutter-Horizontal:after {
    background-color: var(--cds-border-inverse);
  }
`;

const Container = styled(Stack)`
  background-color: var(--cds-layer-accent);
  padding: 0 var(--cds-spacing-05);
  display: flex;
  align-items: center;
  min-height: var(--cds-spacing-08);
  height: var(--cds-spacing-08);
  ${styles.bodyCompact01};
  color: var(--cds-text-primary);
`;

const DiagramWrapper = styled.section`
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
`;

const Label = styled.label`
  ${styles.headingCompact01};
  color: var(--cds-text-secondary);
  align-self: center;
`;

const FieldContainer = styled.div`
  display: flex;
  align-items: center;
`;

export {DiagramContainer, DiagramWrapper, Container, FieldContainer, Label};
