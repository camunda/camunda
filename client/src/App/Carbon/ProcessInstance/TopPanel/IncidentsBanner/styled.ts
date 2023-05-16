/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {styles, supportError} from '@carbon/elements';
import {Stack as BaseStack} from '@carbon/react';
import {WarningFilled as BaseWarningFilled} from '@carbon/react/icons';
import {zIncidentBanner} from 'modules/constants/carbonComponentHierarchy';

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
