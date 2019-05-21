/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled.js';

function IncidentByError({label, incidentsCount, className, perWorkflow}) {
  return (
    <div className={className}>
      <Styled.Wrapper perWorkflow={perWorkflow}>
        <Styled.IncidentsCount>{incidentsCount}</Styled.IncidentsCount>
        <Styled.Label>{label}</Styled.Label>
      </Styled.Wrapper>
      <Styled.IncidentBar />
    </div>
  );
}

IncidentByError.propTypes = {
  label: PropTypes.string.isRequired,
  incidentsCount: PropTypes.number.isRequired,
  className: PropTypes.string,
  perWorkflow: PropTypes.bool
};

export default IncidentByError;
