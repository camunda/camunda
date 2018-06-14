import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default class Checkbox extends React.Component {
  static propTypes = {
    checked: PropTypes.bool.isRequired,
    onChange: PropTypes.func.isRequired,
    indeterminate: PropTypes.bool,
    label: PropTypes.string
  };

  componentDidMount() {
    this.el.indeterminate = this.props.indeterminate;
  }

  componentDidUpdate(prevProps) {
    if (prevProps.indeterminate !== this.props.indeterminate) {
      this.el.indeterminate = this.props.indeterminate;
    }
  }

  render() {
    const {label, indeterminate, checked, onChange} = this.props;
    return (
      <div>
        <input
          {...indeterminate}
          type="checkbox"
          checked={checked}
          onChange={onChange}
          ref={el => (this.el = el)}
        />
        {label && <Styled.Label>{label}</Styled.Label>}
      </div>
    );
  }
}
