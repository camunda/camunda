import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

import {MESSAGES} from './constants';

export default function ContextualMessage(props) {
  return (
    <Styled.Message {...props}>
      <Styled.Dot />
      <Styled.Text data-test="contextual-message-test">
        {MESSAGES[props.type]}
      </Styled.Text>
    </Styled.Message>
  );
}

ContextualMessage.propTypes = {
  type: PropTypes.string.isRequired
};
