/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
`;

export {DiagramContainer, DiagramWrapper, Label, Container};
