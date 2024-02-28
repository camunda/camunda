/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {
  ActionableNotification as BaseActionableNotification,
  InlineNotification as BaseInlineNotification,
} from '@carbon/react';

const Text = styled.p`
  margin: 0;
  ${styles.bodyShort01};
`;

const notificationStyles = css`
  max-width: unset;
  margin-top: var(--cds-spacing-06);
`;

const ActionableNotification = styled(BaseActionableNotification)`
  ${notificationStyles}
`;

const InlineNotification = styled(BaseInlineNotification)`
  ${notificationStyles}
`;

export {Text, ActionableNotification, InlineNotification};
