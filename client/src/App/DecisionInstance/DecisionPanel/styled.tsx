/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const Container = styled.div`
  ${({theme}) => css`
    background: ${theme.colors.decisionPanel.background};
  `}

  padding: 30px 20px;
`;

export {Container};
