/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';
import MultiRow from 'modules/components/MultiRow';
import EmptyPanel from 'modules/components/EmptyPanel';
import {MESSAGES} from './constants';
import PropTypes from 'prop-types';

const MultiRowContainer = (props) => {
  return (
    <Styled.MultiRowContainer data-testid="skeleton">
      <MultiRow Component={Styled.Block} {...props} />
    </Styled.MultiRowContainer>
  );
};

const Skeleton = ({data, isFailed, isLoaded, errorType}) => {
  let label, type, placeholder, rowHeight;

  if (isFailed) {
    type = 'warning';
    label = MESSAGES[errorType].error;
  } else if (!isLoaded) {
    type = 'skeleton';
    placeholder = MultiRowContainer;
    rowHeight = 55;
  } else if (data.length === 0) {
    type = 'info';
    label = MESSAGES[errorType].noData;
  }
  return (
    <EmptyPanel
      type={type}
      label={label}
      Skeleton={placeholder}
      rowHeight={rowHeight}
    />
  );
};

Skeleton.propTypes = {
  data: PropTypes.array,
  isFailed: PropTypes.bool,
  isLoaded: PropTypes.bool,
  errorType: PropTypes.string,
};

export {Skeleton};
