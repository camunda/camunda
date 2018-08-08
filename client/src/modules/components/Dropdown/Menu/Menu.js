import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class Menu extends React.Component {
  static propTypes = {
    /** This defines if the dropdown opens to the top or bottom.*/
    placement: PropTypes.oneOf(['top', 'bottom']),
    /** The options of this dropdown. Each child should be a `Dropdown.Option` instance */
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  render() {
    return (
      <Styled.DropdownMenu placement={this.props.placement}>
        {this.props.children}
      </Styled.DropdownMenu>
    );
  }
}
