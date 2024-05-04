/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';

const Container = styled.span`
  color: var(--cds-text-secondary);
  text-align: center;
  padding-top: var(--cds-spacing-05);

  &,
  & a {
    ${styles.legal01};
  }
`;

export {Container};
