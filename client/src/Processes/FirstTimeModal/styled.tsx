/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {
  ActionableNotification as BaseActionableNotification,
  Tag as BaseTag,
} from '@carbon/react';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: var(--cds-text-primary);
`;

const Image = styled.img`
  margin-bottom: var(--cds-spacing-08);
`;

const ActionableNotification = styled(BaseActionableNotification)`
  margin-top: var(--cds-spacing-08);
`;

const Tag = styled(BaseTag)`
  margin: 0;
`;

export {Container, Image, ActionableNotification, Tag};
