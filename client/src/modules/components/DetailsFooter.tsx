/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const DetailsFooter = styled.div`
  background-color: ${({theme}) => theme.colors.ui02};
  box-shadow: ${({theme}) => theme.shadows.detailsFooter};
  text-align: right;
  padding: 14px 19px;
`;

export {DetailsFooter};
