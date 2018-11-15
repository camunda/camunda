import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled.js';

function IncidentStatistic(props) {
  const {label, activeCount, incidentsCount} = props;
  const incidentsBarRatio =
    (100 * incidentsCount) / (activeCount + incidentsCount);
  return (
    <div className={props.className}>
      <Styled.Wrapper perUnit={props.perUnit}>
        <Styled.IncidentsCount>{incidentsCount}</Styled.IncidentsCount>
        <Styled.Label>{label}</Styled.Label>
        <Styled.ActiveCount>{activeCount}</Styled.ActiveCount>
      </Styled.Wrapper>
      <Styled.IncidentStatisticBar>
        <Styled.IncidentsBar
          style={{
            width: `${incidentsBarRatio}%`
          }}
        />
      </Styled.IncidentStatisticBar>
    </div>
  );
}

IncidentStatistic.propTypes = {
  label: PropTypes.string.isRequired,
  activeCount: PropTypes.number.isRequired,
  incidentsCount: PropTypes.number.isRequired,
  className: PropTypes.string,
  perUnit: PropTypes.bool
};

export default IncidentStatistic;
