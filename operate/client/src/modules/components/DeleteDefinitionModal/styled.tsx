/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';

const Description = styled.p`
  margin: 0;
  ${styles.bodyShort01};
`;

const WarningContainer = styled.section`
  margin-top: var(--cds-spacing-06);
`;

export {Description, WarningContainer};
