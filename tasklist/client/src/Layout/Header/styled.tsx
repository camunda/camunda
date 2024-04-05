/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Link} from '@carbon/react';
import styled from 'styled-components';

const InlineLink = styled(Link)`
  display: inline;
  color: var(--cds-link-primary);

  &:visited,
  &:visited:hover {
    color: var(--cds-link-primary);
  }
`;

export {InlineLink};
