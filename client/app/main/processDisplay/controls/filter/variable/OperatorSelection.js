import React from 'react';
import {OperatorButton} from './OperatorButton';
import {createViewUtilsComponentFromReact} from 'reactAdapter';

const jsx = React.createElement;

export class OperatorSelectionReact extends React.PureComponent {
  render() {
    return <div className="operators">
      {this.getOperators()}
    </div>;
  }

  getOperators() {
    const {operator, value} = this.props;

    if (this.variableHasType('String')) {
      return [
        <OperatorButton key="in" operator="in" selectedOperator={operator} value={value} />,
        <OperatorButton key="not in" operator="not in" selectedOperator={operator} value={value} />
      ];
    }

    if (this.variableHasType('Boolean')) {
      return [
        <OperatorButton key="true" operator="=" implicitValue={true} selectedOperator={operator} value={value} />,
        <OperatorButton key="false" operator="=" implicitValue={false} selectedOperator={operator} value={value} />
      ];
    }

    if (this.variableSelected()) {
      return [
        <OperatorButton key="in" operator="in" selectedOperator={operator} value={value} />,
        <OperatorButton key="not in" operator="not in" selectedOperator={operator} value={value} />,
        <OperatorButton key=">" operator=">" selectedOperator={operator} value={value} />,
        <OperatorButton key="<" operator="<" selectedOperator={operator} value={value} />
      ];
    }

    return null;
  }

  variableHasType(type) {
    const {variables, selectedIdx} = this.props;

    return variables && variables.data && selectedIdx !== undefined && variables.data[selectedIdx].type === type;
  }

  variableSelected() {
    return this.props.selectedIdx !== undefined;
  }
}

export const OperatorSelection = createViewUtilsComponentFromReact('span', OperatorSelectionReact);

