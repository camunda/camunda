/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';

const TimeStamp = styled.span`
  padding: 0 var(--cds-spacing-03);
  background: var(--cds-layer);
  ${styles.label01};
  border-radius: 2px;
`;

export {TimeStamp};
