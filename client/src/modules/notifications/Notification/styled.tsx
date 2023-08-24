/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {
  ToastNotification as BaseToastNotification,
  ActionableNotification as BaseActionableNotification,
} from '@carbon/react';

const ToastNotification = styled(BaseToastNotification)`
  margin-top: var(--cds-spacing-03);
`;

const ActionableNotification = styled(BaseActionableNotification)`
  margin-top: var(--cds-spacing-03);
`;

export {ToastNotification, ActionableNotification};
