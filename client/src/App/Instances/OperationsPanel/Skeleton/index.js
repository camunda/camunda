/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';

function doesMatchMinHeight(height) {
  return window.matchMedia(`(min-height: ${height}px)`).matches;
}

export function OperationEntry() {
  return (
    <Styled.Entry>
      <Styled.EntryStatus>
        <div>
          <Styled.Type />
          <Styled.Id />
        </div>
        <Styled.OperationIcon />
      </Styled.EntryStatus>
      <Styled.EntryDetails>
        <Styled.InstancesCount />
      </Styled.EntryDetails>
    </Styled.Entry>
  );
}

export default function Skeleton() {
  return (
    <div data-test="skeleton">
      <OperationEntry />
      {doesMatchMinHeight(400) && <OperationEntry />}
      {doesMatchMinHeight(530) && <OperationEntry />}
      {doesMatchMinHeight(660) && <OperationEntry />}
      {doesMatchMinHeight(790) && <OperationEntry />}
      {doesMatchMinHeight(920) && <OperationEntry />}
      {doesMatchMinHeight(1050) && <OperationEntry />}
    </div>
  );
}
