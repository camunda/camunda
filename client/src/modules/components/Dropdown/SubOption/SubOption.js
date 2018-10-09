import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class SubOption extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    onStateChange: PropTypes.func,
    onClick: PropTypes.func
  };

  handleOnClick = evt => {
    evt && evt.stopPropagation();
    this.props.onClick();
    this.props.onStateChange({isOpen: false});
  };

  render() {
    return (
      <Styled.OptionButton onClick={this.handleOnClick}>
        {this.props.children}
      </Styled.OptionButton>
    );
  }
}
