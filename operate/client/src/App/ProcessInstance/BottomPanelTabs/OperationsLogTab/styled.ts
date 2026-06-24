/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const Container = styled.section`
  height: 100%;
  display: flex;
  flex-direction: column;

  [role='table'] {
    table-layout: fixed;
  }

  td {
    overflow: hidden;
    text-overflow: ellipsis;
  }
`;

const EmptyMessageContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
`;

export {Container, EmptyMessageContainer};
