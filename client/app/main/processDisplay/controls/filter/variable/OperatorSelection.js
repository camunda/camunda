import {jsx, Match, Case} from 'view-utils';
import {OperatorButton} from './OperatorButton';

export function OperatorSelection() {
  return <div className="operators">
    <Match>
      <Case predicate={variableHasType('String')}>
        <OperatorButton operator="=" />
        <OperatorButton operator="!=" />
      </Case>
      <Case predicate={variableHasType('Boolean')}>
        <OperatorButton operator="=" implicitValue={true} />
        <OperatorButton operator="=" implicitValue={false} />
      </Case>
      <Case predicate={variableSelected}>
        <OperatorButton operator="=" />
        <OperatorButton operator="!=" />
        <OperatorButton operator=">" />
        <OperatorButton operator="<" />
      </Case>
    </Match>
  </div>;

  function variableHasType(type) {
    return ({variables: {data}, selectedIdx}) => {
      return data && selectedIdx !== undefined && data[selectedIdx].type === type;
    };
  }

  function variableSelected({selectedIdx}) {
    return selectedIdx !== undefined;
  }
}
