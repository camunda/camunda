import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default class Checkbox extends React.Component {
  static propTypes = {
    isChecked: PropTypes.bool,
    onChange: PropTypes.func.isRequired,
    isIndeterminate: PropTypes.bool,
    type: PropTypes.oneOf(['selection']),
    label: PropTypes.string
  };

  constructor(props) {
    super(props);
    this.state = {isChecked: false};
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
    this.setState({isChecked: !this.state.isChecked}, () => {
      this.props.onChange({isChecked: this.el.checked});
    });
  };

  inputRef = node => {
    this.el = node;
  };

  render() {
    const {label, isIndeterminate, type} = this.props;

    return (
      <Styled.Checkbox onClick={this.handleOnClick}>
        <Styled.Input
          data-test="checkbox-input"
          indeterminate={isIndeterminate}
          type="checkbox"
          checked={this.state.isChecked}
          innerRef={this.inputRef}
          checkboxType={type}
        />
        <Styled.CustomCheckbox {...{isIndeterminate}} checkboxType={type} />
        {label && <Styled.Label>{label}</Styled.Label>}
      </Styled.Checkbox>
    );
  }
}
