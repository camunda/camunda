/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

const Container = styled.div`
  height: 100%;
  padding: var(--cds-spacing-08);
  background-color: var(--cds-background);
`;

const Content = styled.div`
  padding: var(--cds-spacing-12);
  width: 100%;
  background-color: var(--cds-layer);
`;

export {Container, Content};
