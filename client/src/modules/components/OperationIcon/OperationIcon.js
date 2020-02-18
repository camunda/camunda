/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_TYPE} from 'modules/constants';
import * as Styled from './styled';

const iconsMap = {
  [OPERATION_TYPE.RESOLVE_INCIDENT]: Styled.Retry,
  [OPERATION_TYPE.UPDATE_VARIABLE]: Styled.Edit,
  [OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE]: Styled.Cancel
};

const OperationIcon = function OperationIcon({operationType, ...props}) {
  const TargetIcon = iconsMap[operationType];

  return (
    <Styled.OperationIcon {...props}>
      <TargetIcon />
    </Styled.OperationIcon>
  );
};

OperationIcon.propTypes = {
  operationType: PropTypes.oneOf(Object.keys(OPERATION_TYPE))
};

export default OperationIcon;
