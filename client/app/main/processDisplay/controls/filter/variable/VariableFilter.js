import {jsx, withSelector, Scope, Text, OnEvent} from 'view-utils';
import {parseVariableFilter} from 'main/processDisplay/query';
import operatorLabels from './labels';

export const VariableFilter = withSelector(({onDelete}) => {
  return <span>
    <button type="button" className="btn btn-link btn-xs pull-right">
      <OnEvent event="click" listener={onDelete} />
      ×
    </button>
    <span>
      <Scope selector={parseVariableFilter}>
        <span className="variable-name">
          <Text property="name" />
        </span>
        &nbsp;
        <span className="variable-operator">
          <Scope selector={getHumanReadableLabelForOperator}>
            <Text />
          </Scope>
        </span>
        &nbsp;
        <span className="variable-value badge">
          <Scope selector={getValueLabel}>
            <Text />
          </Scope>
        </span>
      </Scope>
    </span>
  </span>;

  function getHumanReadableLabelForOperator({operator}) {
    return operatorLabels[operator];
  }

  function getValueLabel({values}) {
    return values.length > 1 ? `${values.length} values` : values[0];
  }
});
