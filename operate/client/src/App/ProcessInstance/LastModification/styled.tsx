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
  align-items: center;
  height: 2rem;
  ${styles.bodyShort01};
  background-color: var(--cds-button-secondary);
  color: var(--cds-text-on-color);
  padding: var(--cds-spacing-03) 0 var(--cds-spacing-03) var(--cds-spacing-05);
`;

const ModificationDetail = styled.div`
  display: inline-flex;
  position: relative;
  padding-right: var(--cds-spacing-05);
  &:after {
    content: ' ';
    position: absolute;
    right: 0;
    height: var(--cds-spacing-05);
    width: 1px;
    background-color: var(--cds-text-on-color);
  }
`;

export {Container, ModificationDetail};
