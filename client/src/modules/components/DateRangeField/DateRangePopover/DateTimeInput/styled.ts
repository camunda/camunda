/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

const Container = styled.div`
  display: flex;
  justify: flex-end;
  align-items: flex-end;
  padding: 16px;

  // Make Carbon input look similar to Operate's current style.
  // Can be removed, once Operate is carbonized (see #3629).
  input {
    border: 1px solid #b0bac7;
    border-radius: 3px;
  }
  .cds--text-input-wrapper {
    width: 98px;
    margin-left: 3px;
  }
`;

export {Container};
