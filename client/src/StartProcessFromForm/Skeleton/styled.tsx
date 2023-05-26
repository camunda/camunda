/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

const Container = styled.div`
  width: 100%;
  display: flex;
  display: grid;

  grid-template-columns: repeat(2, 1fr);
  grid-template-rows: repeat(4, min-content);
  grid-gap: var(--cds-spacing-04);

  & > :nth-child(1) {
    grid-area: 1 / 1 / 2 / 2;
  }
  & > :nth-child(2) {
    grid-area: 2 / 1 / 3 / 3;
  }
  & > :nth-child(3) {
    grid-area: 3 / 1 / 4 / 2;
  }
  & > :nth-child(4) {
    grid-area: 3 / 2 / 4 / 3;
  }
  & > :nth-child(5) {
    grid-area: 4 / 1 / 5 / 3;
  }
`;

export {Container};
