import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled.js';

function IncidentByError(props) {
  const {label, incidentsCount} = props;
  return (
    <div className={props.className}>
      <Styled.Wrapper perUnit={props.perUnit}>
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
  perUnit: PropTypes.bool
};

export default IncidentByError;
