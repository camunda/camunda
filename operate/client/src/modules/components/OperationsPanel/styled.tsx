/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const OperationsList = styled.div<{$height: number}>`
  position: relative;
  width: 100%;
  height: ${({$height}) => $height}px;
`;

const ScrollContainer = styled.div<{$height: number; $transform: number}>`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  transform: translateY(${({$transform}) => $transform}px);
  height: ${({$height}) => $height}px;
`;

const EmptyMessageContainer = styled.div`
  padding: var(--cds-spacing-05);
`;

export {OperationsList, EmptyMessageContainer, ScrollContainer};
