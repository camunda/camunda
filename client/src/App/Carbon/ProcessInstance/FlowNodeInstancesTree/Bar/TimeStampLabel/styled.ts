/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
