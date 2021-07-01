/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const DisplayText = styled.div`
  line-height: 18px;
  word-break: break-word;
  margin: 11px 0;
  max-height: 76px;
  overflow-y: auto;
  overflow-wrap: break-word;
`;

const Container = styled.div`
  display: flex;
  padding-right: 14px;
  align-items: center;
  width: 100%;
  min-width: 400px;
`;

const Name = styled.div`
  font-weight: 600;
  height: 100%;
  padding: 4px 23px 4px 14px;
  margin: 3px 0;
  line-height: 18px;
  display: block;
  text-overflow: ellipsis;
  overflow: hidden;
  width: 30%;
`;

const Value = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 60%;
`;

const SpinnerContainer = styled.div`
  width: 10%;
  display: flex;
  justify-content: flex-end;
`;

export {DisplayText, Container, Name, Value, SpinnerContainer};
