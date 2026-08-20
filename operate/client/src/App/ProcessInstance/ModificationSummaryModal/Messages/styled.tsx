/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
