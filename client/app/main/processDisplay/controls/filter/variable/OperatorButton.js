import React from 'react';
import {setOperator, setValue} from './service';
import labels from './labels';

const jsx = React.createElement;

export class OperatorButton extends React.PureComponent {
  render() {
    return <button type="button" className={'btn btn-default' + (this.isSelectedOperator() ? ' active' : '')}
                   style={{width: '22%'}}
                   onClick={this.applyOperator}>
      {this.getAggregatedLabel()}
    </button>;
  }

  applyOperator = () => {
    const {operator, implicitValue} = this.props;

    setOperator(operator);

    if (implicitValue !== undefined) {
      setValue(implicitValue);
    }
  }

  getAggregatedLabel() {
    const {operator, implicitValue} = this.props;

    return `${labels[operator]} ${implicitValue !== undefined ? labels[implicitValue.toString()] : ''}`.trim();
  }

  isSelectedOperator() {
    const {operator, implicitValue, selectedOperator, value} = this.props;
    const matchingOperator = selectedOperator === operator;
    const matchingValue = implicitValue !== undefined ? value === implicitValue : true;

    return matchingOperator && matchingValue;
  }
}
