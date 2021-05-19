/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const DisplayText = styled.div`
  line-height: 18px;
  word-break: break-word;
  margin: 11px 94px 11px 0;
  max-height: 76px;
  overflow-y: auto;
  overflow-wrap: break-word;
`;

const Container = styled.div`
  display: flex;
  padding: 0 20px 0 16px;
  align-items: center;
  width: 100%;
`;

const Name = styled.div`
  font-weight: bold;
  height: 100%;
  padding: 4px 0;
  margin: 3px 0;
  line-height: 18px;
  display: block;
  text-overflow: ellipsis;
  overflow: hidden;
  min-width: 226px;
`;

const Value = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
`;

export {DisplayText, Container, Name, Value};
