import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

import {MESSAGES} from './constants';

export default class ContextualMessage extends React.Component {
  static propTypes = {
    type: PropTypes.string.isRequired
  };

  render() {
    return (
      <Styled.Message>
        <Styled.Dot />
        <Styled.Text data-test="contextual-message-test">
          {MESSAGES[this.props.type]}
        </Styled.Text>
      </Styled.Message>
    );
  }
}
