/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const Container = styled.div`
  background: white;
  display: flex;
  position: absolute;
  width: 540px;
  height: 100%;
  right: 0;
`;

const Handle = styled.div`
  cursor: ew-resize;
  width: 10px;
  height: 100%;
`;

export {Container, Handle};
