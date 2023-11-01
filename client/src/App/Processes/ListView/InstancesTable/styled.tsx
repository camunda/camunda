/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

const Container = styled.section`
  height: 100%;
  display: flex;
  flex-direction: column;
`;

const ProcessName = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-04);
`;

export {Container, ProcessName};
