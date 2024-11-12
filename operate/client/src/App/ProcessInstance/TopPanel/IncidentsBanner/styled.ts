/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles, supportError} from '@carbon/elements';
import {Stack as BaseStack} from '@carbon/react';
import {WarningFilled as BaseWarningFilled} from '@carbon/react/icons';
import {zIncidentBanner} from 'modules/constants/componentHierarchy';

const IncidentBanner = styled.button`
  display: flex;
  justify-content: center;
  align-items: center;
  height: var(--cds-spacing-09);
  position: relative;
  z-index: ${zIncidentBanner};
  ${styles.bodyShort01};
  background-color: var(--cds-notification-background-error);
  border: 1px solid var(--cds-support-error);
  border-left: 4px solid var(--cds-support-error);
  color: var(--cds-text-primary);
  cursor: pointer;
`;

const Stack = styled(BaseStack)`
  align-items: center;
`;
const WarningFilled = styled(BaseWarningFilled)`
  fill: ${supportError};
`;

export {IncidentBanner, Stack, WarningFilled};
