/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

import {
  WarningFilled as BaseWarningFilled,
  CheckmarkOutline as BaseCheckmarkOutline,
} from '@carbon/react/icons';

const WarningFilled = styled(BaseWarningFilled)`
  fill: var(--cds-support-error);
`;

const CheckmarkOutline = styled(BaseCheckmarkOutline)`
  fill: var(--cds-icon-secondary);
`;

export {WarningFilled, CheckmarkOutline};
