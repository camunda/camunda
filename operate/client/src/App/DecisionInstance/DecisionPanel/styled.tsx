/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';

const Section = styled.section`
  height: 100%;
  overflow: auto;
`;

const IncidentBanner = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  height: var(--cds-spacing-09);
  ${styles.bodyShort01};
  background-color: var(--cds-notification-background-error);
  border: 1px solid var(--cds-support-error);
  border-left: 4px solid var(--cds-support-error);
  color: var(--cds-text-primary);
`;

export {Section, IncidentBanner};
