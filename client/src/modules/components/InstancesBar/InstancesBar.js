/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled.js';

function InstancesBar(props) {
  const {label, activeCount, incidentsCount, size} = props;
  const incidentsBarRatio =
    (100 * incidentsCount) / (activeCount + incidentsCount);

  const hasIncidents = incidentsCount > 0;
  const hasActive = activeCount > 0;

  return (
    <div className={props.className}>
      <Styled.Wrapper size={size}>
        <Styled.IncidentsCount hasIncidents={hasIncidents}>
          {incidentsCount}
        </Styled.IncidentsCount>
        <Styled.Label>{label}</Styled.Label>
        <Styled.ActiveCount hasActive={hasActive}>
          {activeCount}
        </Styled.ActiveCount>
      </Styled.Wrapper>
      <Styled.BarContainer size={size}>
        <Styled.Bar hasActive={hasActive} />
        <Styled.IncidentsBar
          style={{
            width: `${incidentsBarRatio}%`
          }}
        />
      </Styled.BarContainer>
    </div>
  );
}

InstancesBar.propTypes = {
  label: PropTypes.string,
  activeCount: PropTypes.number.isRequired,
  incidentsCount: PropTypes.number.isRequired,
  className: PropTypes.string,
  size: PropTypes.oneOf(['small', 'medium', 'large']).isRequired
};

export default InstancesBar;
