/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {styles} from '@carbon/elements';
import styled from 'styled-components';

const Grid = styled.div`
  display: grid;
  justify-content: center;
  align-content: center;
  grid-template-columns: 80px 334px;
  column-gap: var(--cds-spacing-06);
  height: 100%;
`;

const Title = styled.h3`
  ${styles.productiveHeading02};
  color: var(--cds-text-secondary);
  margin: 0;
  padding: 0;
`;

const Description = styled.p`
  color: var(--cds-text-secondary);
  margin: 0 0 var(--cds-spacing-05);
`;

export {Grid, Title, Description};
