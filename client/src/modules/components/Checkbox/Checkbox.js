import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default class Checkbox extends React.Component {
  static propTypes = {
    isChecked: PropTypes.bool,
    /** Receives object with single property - isChecked: `boolean` */
    onChange: PropTypes.func.isRequired,
    isIndeterminate: PropTypes.bool,
    label: PropTypes.string,
    name: PropTypes.string,
    type: PropTypes.oneOf(['selection']),
    value: PropTypes.string,
    title: PropTypes.string
  };

  constructor(props) {
    super(props);
    this.el = {};
  }

  componentDidMount() {
    const {isIndeterminate, isChecked} = this.props;

    if (isIndeterminate) {
      this.el.indeterminate = isIndeterminate;
    }
    this.setState({isChecked});
  }

  componentDidUpdate(prevProps) {
    const {isIndeterminate, isChecked} = this.props;

    if (prevProps.isIndeterminate !== isIndeterminate) {
      this.el.indeterminate = isIndeterminate;
    }

    if (prevProps.isChecked !== isChecked) {
      this.setState({isChecked});
    }
  }

  handleOnClick = () => {
    this.props.onChange({isChecked: this.el.checked});
  };

  inputRef = node => {
    this.el = node;
  };

  render() {
    const {
      label,
      onChange,
      isIndeterminate,
      isChecked,
      type,
      title,
      ...other
    } = this.props;
    return (
      <Styled.Checkbox onClick={this.handleOnClick}>
        <Styled.Input
          data-test="checkbox-input"
          indeterminate={isIndeterminate}
          type="checkbox"
          checked={this.props.isChecked}
          innerRef={this.inputRef}
          checkboxType={type}
          onChange={event => event}
          aria-label={label || title}
          title={label || title}
          {...other}
        />
        <Styled.CustomCheckbox
          {...{isIndeterminate}}
          checkboxType={type}
          aria-label={label || title}
          title={label || title}
        />
        {label && (
          <Styled.Label checked={isChecked || isIndeterminate}>
            {label}
          </Styled.Label>
        )}
      </Styled.Checkbox>
    );
  }
}
