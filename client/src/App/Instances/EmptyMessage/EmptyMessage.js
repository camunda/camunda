import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

export default function EmptyMessage({message, ...props}) {
  return (
    <Styled.EmptyMessage {...props}>
      {message.split('\n').map((item, index) => (
        <span key={index}>{item}</span>
      ))}
    </Styled.EmptyMessage>
  );
}

EmptyMessage.propTypes = {
  message: PropTypes.string.isRequired
};
