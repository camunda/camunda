/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {Breadcrumb} from '@carbon/react';

const CarbonBreadcrumb = styled(Breadcrumb)`
  display: flex;
  align-items: center;
  background-color: var(--cds-layer-01);
  border-bottom: 1px solid var(--cds-border-subtle-01);
  padding-left: var(--cds-spacing-05);
`;

export {CarbonBreadcrumb};
