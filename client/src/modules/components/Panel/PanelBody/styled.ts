/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

type BodyProps = {
  scrollable?: boolean;
};

const Body = styled.div<BodyProps>`
  ${({scrollable}) => {
    return css`
      overflow: ${scrollable ? 'auto' : 'hidden'};
      flex-grow: 1;
      display: flex;
      flex-direction: column;
    `;
  }}
`;

export {Body};
