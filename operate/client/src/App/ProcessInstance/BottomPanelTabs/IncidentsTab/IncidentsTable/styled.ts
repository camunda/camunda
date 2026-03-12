/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const ErrorMessageCell = styled.div`
  max-width: 404px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const FlexContainer = styled.div`
  display: flex;
  align-items: center;
`;

export {ErrorMessageCell, FlexContainer};
