/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
