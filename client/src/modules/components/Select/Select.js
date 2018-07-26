import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

export default function Select(props) {
  const {options, placeholder, ...otherProps} = props;

  return (
    <Styled.Select
      {...otherProps}
      aria-label={placeholder}
      aria-disabled={props.disabled}
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

Select.propTypes = {
  options: PropTypes.array.isRequired,
  placeholder: PropTypes.string,
  disabled: PropTypes.bool
};

Select.defaulProps = {
  placeholder: 'Select'
};
