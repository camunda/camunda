import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class Menu extends React.Component {
  static propTypes = {
    onKeyDown: PropTypes.func.isRequired,
    /** This defines if the dropdown opens to the top or bottom.*/
    placement: PropTypes.oneOf(['top', 'bottom']),
    /** The options of this dropdown. Each child should be a `Dropdown.Option` instance */
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    onStateChange: PropTypes.func
  };

  render() {
    const {onKeyDown, placement, children} = this.props;

    return (
      <Styled.Ul placement={placement}>
        {React.Children.map(children, (child, index) => (
          <Styled.Li
            onKeyDown={onKeyDown}
            placement={placement}
            key={index}
            tabIndex={index + 1}
          >
            {child}
          </Styled.Li>
        ))}
      </Styled.Ul>
    );
  }
}
