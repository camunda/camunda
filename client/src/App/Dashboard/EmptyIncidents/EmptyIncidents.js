/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

function EmptyIncidents({type, label}) {
  return (
    <Styled.EmptyIncidents>
      {type === 'warning' && <Styled.WarningIcon />}
      <Styled.Label type={type}>{label}</Styled.Label>
    </Styled.EmptyIncidents>
  );
}

EmptyIncidents.propTypes = {
  label: PropTypes.string,
  type: PropTypes.oneOf(['info', 'warning'])
};

export default EmptyIncidents;
