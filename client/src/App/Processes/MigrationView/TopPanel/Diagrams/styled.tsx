/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

const DiagramContainer = styled.div`
  display: flex;
  flex-grow: 1;
  .custom-gutter-Horizontal:after {
    background-color: var(--cds-border-inverse);
  }
`;

const DiagramWrapper = styled.section`
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
`;

export {DiagramContainer, DiagramWrapper};
