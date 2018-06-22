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

  state = {isChecked: false};

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

  render() {
    const {label, isIndeterminate, type} = this.props;

    return (
      <Styled.Checkbox onClick={this.handleOnClick} data-test-id="checkbox">
        <Styled.Input
          data-test-id="default-input"
          indeterminate={isIndeterminate}
          type="checkbox"
          checked={this.state.isChecked}
          innerRef={el => (this.el = el)}
          checkboxType={type}
        />
        <Styled.CustomCheckbox {...{isIndeterminate}} checkboxType={type} />
        {label && <Styled.Label data-test-id="label">{label}</Styled.Label>}
      </Styled.Checkbox>
    );
  }
}
