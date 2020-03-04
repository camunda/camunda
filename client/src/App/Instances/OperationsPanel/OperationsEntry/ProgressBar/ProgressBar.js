/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

function getBarWidth(totalCount, finishedCount) {
  if (totalCount === 0) {
    return 0;
  }
  return Math.floor((100 / totalCount) * finishedCount);
}

const ProgressBar = ({totalCount, finishedCount}) => {
  const barWidth = getBarWidth(totalCount, finishedCount);

  return (
    <Styled.Container>
      <Styled.Background />
      <Styled.Bar width={barWidth} data-test="progress-bar" />
    </Styled.Container>
  );
};

ProgressBar.propTypes = {
  totalCount: PropTypes.number.isRequired,
  finishedCount: PropTypes.number.isRequired
};

export default ProgressBar;
