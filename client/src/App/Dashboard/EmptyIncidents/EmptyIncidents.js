import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

function EmptyIncidents(props) {
  return (
    <Styled.EmptyIncidents>
      {props.type === 'success' ? <Styled.CheckIcon /> : <Styled.WarningIcon />}
      <Styled.Label type={props.type}>{props.label}</Styled.Label>
    </Styled.EmptyIncidents>
  );
}

EmptyIncidents.propTypes = {
  label: PropTypes.string,
  type: PropTypes.oneOf(['success', 'warning'])
};

export default EmptyIncidents;
