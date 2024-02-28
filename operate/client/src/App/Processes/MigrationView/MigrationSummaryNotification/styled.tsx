/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
