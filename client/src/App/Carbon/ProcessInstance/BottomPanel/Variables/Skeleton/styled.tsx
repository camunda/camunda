/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {SkeletonText as BaseSkeletonText} from '@carbon/react';
import styled from 'styled-components';

const Ul = styled.ul`
  overflow: hidden;
`;

const Li = styled.li`
  margin: var(--cds-spacing-05);
  display: flex;
  gap: var(--cds-spacing-05);
`;

const SkeletonText = styled(BaseSkeletonText)`
  margin: 0;
`;

export {Ul, Li, SkeletonText};
