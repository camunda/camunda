/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const OperationsList = styled.div`
  position: relative;
  width: 100%;
`;

const ScrollContainer = styled.div`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
`;

const EmptyMessageContainer = styled.div`
  padding: var(--cds-spacing-05);
`;

export {OperationsList, EmptyMessageContainer, ScrollContainer};
