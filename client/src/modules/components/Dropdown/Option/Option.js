import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class Option extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  render() {
    return <Styled.Option {...this.props}>{this.props.children}</Styled.Option>;
  }
}
