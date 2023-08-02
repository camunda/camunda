/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';

const Container = styled.div`
  display: flex;
  justify-content: center;
  ${styles.helperText01};
  background-color: var(--cds-notification-background-warning);
  border: 1px solid var(--cds-support-warning);
  box-shadow: 0 2px 6px var(--cds-shadow);
`;

const Text = styled.div`
  padding: var(--cds-spacing-03);
`;

export {Container, Text};
