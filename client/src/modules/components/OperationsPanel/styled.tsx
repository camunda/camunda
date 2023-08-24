/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

const OperationsList = styled.ul`
  li:last-child {
    border-bottom: none;
  }
`;

const EmptyMessageContainer = styled.div`
  padding: var(--cds-spacing-05);
`;

export {OperationsList, EmptyMessageContainer};
