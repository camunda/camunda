import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class Option extends React.Component {
  static propTypes = {
    label: PropTypes.node,
    disabled: PropTypes.bool,
    isSubMenuOpen: PropTypes.bool,
    isSubmenuFixed: PropTypes.bool,
    onStateChange: PropTypes.func,
    onClick: PropTypes.func,
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    className: PropTypes.string
  };

  handleOnClick = () => {
    if (!this.props.disabled && this.props.onClick) {
      this.props.onClick();
      this.props.onStateChange({isOpen: false});
    }
  };

  renderChildrenProps = () =>
    React.Children.map(this.props.children, (child, idx) =>
      React.cloneElement(child, {
        isOpen: this.props.isSubMenuOpen,
        isFixed: this.props.isSubmenuFixed,
        onStateChange: this.props.onStateChange
      })
    );

  render() {
    const {children, disabled, label, className} = this.props;

    return (
      <Styled.Option
        label={label}
        disabled={disabled}
        onClick={() => this.handleOnClick()}
        className={className}
        data-test="dropdown-option"
      >
        {!children ? (
          <Styled.OptionButton disabled={disabled}>{label}</Styled.OptionButton>
        ) : (
          this.renderChildrenProps()
        )}
      </Styled.Option>
    );
  }
}
