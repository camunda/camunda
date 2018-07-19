import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

export default class Select extends React.Component {
  static propTypes = {
    options: PropTypes.array.isRequired,
    placeholder: PropTypes.string,
    disabled: PropTypes.bool
  };

  static defaultProps = {
    placeholder: 'Select'
  };

  render() {
    const {options, placeholder, ...otherProps} = this.props;
    return (
      <Styled.Select
        {...otherProps}
        aria-label={placeholder}
        aria-disabled={this.props.disabled}
      >
        <option value="">{placeholder}</option>
        {options.map(option => {
          return (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          );
        })}
      </Styled.Select>
    );
  }
}
