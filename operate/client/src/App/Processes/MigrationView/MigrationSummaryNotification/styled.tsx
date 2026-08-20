/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {
  InlineNotification as BaseInlineNotification,
  Stack,
} from '@carbon/react';
import {styles} from '@carbon/elements';

const InlineNotification = styled(BaseInlineNotification)`
  max-width: unset;
`;

const MigrationSummary = styled(Stack)`
  p {
    ${styles.bodyCompact01};
  }
`;

export {InlineNotification, MigrationSummary};
