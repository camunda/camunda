import {jsx, OnEvent, Class, Scope, Text} from 'view-utils';
import {setOperator, setValue} from './service';
import labels from './labels';

export function OperatorButton({operator, implicitValue}) {
  return <button type="button" className="btn btn-default" style="width:22%;">
    <OnEvent event="click" listener={applyOperator} />
    <Class className="active" predicate={isSelectedOperator} />
    <Scope selector={getAggregatedLabel}>
      <Text property="label" />
    </Scope>
  </button>;

  function getAggregatedLabel() {
    return {label:
      `${labels[operator]} ${implicitValue ? labels[implicitValue] : ''}`.trim()
    };
  }

  function applyOperator() {
    setOperator(operator);
    if (implicitValue) {
      setValue(implicitValue);
    }
  }

  function isSelectedOperator(state) {
    const matchingOperator = state.operator === operator;
    const matchingValue = implicitValue ? state.value === implicitValue : true;

    return matchingOperator && matchingValue;
  }
}
