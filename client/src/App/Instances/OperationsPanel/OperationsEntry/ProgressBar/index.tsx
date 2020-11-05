/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import * as Styled from './styled';

function getBarWidth(totalCount: any, finishedCount: any) {
  if (totalCount === 0) {
    return 0;
  }
  return Math.floor((100 / totalCount) * finishedCount);
}

type Props = {
  totalCount: number;
  finishedCount: number;
};

const ProgressBar = ({totalCount, finishedCount}: Props) => {
  const barWidth = getBarWidth(totalCount, finishedCount);

  return (
    <Styled.Container>
      <Styled.Background />
      <Styled.Bar width={barWidth} data-testid="progress-bar" />
    </Styled.Container>
  );
};

export default ProgressBar;
