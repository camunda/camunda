import React from 'react';

import {ButtonGroup, Button} from 'components';
import classnames from 'classnames';

export default class BooleanInput extends React.Component {
  static defaultFilter = {value: true};

  componentDidMount() {
    this.props.setValid(true);
  }

  setOperator = value => evt => {
    evt.preventDefault();
    this.props.changeFilter({value});
  };

  render() {
    return (
      <div className="VariableFilter__buttonRow">
        <ButtonGroup>
          <Button
            onClick={this.setOperator(true)}
            className={classnames({'is-active': this.props.filter.value === true})}
          >
            is true
          </Button>
          <Button
            onClick={this.setOperator(false)}
            className={classnames({'is-active': this.props.filter.value === false})}
          >
            is false
          </Button>
        </ButtonGroup>
      </div>
    );
  }
}
