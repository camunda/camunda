/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

const Header = styled.header`
  background-color: var(--cds-layer);
  border-bottom: solid 1px var(--cds-border-subtle-01);
  padding: var(--cds-spacing-04) var(--cds-spacing-05);
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: var(--cds-spacing-09);
  height: var(--cds-spacing-09);
`;

export {Header};
