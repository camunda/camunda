import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

export default function VerticalExpandButton({label, children, ...otherProps}) {
  return (
    <Styled.Button title={`Expand ${label}`} {...otherProps}>
      <Styled.Vertical>
        <Styled.Label>{label}</Styled.Label>
        {children}
      </Styled.Vertical>
    </Styled.Button>
  );
}

VerticalExpandButton.propTypes = {
  label: PropTypes.string.isRequired,
  children: PropTypes.node
};
