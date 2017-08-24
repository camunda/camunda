import {jsx, Scope, List, Match, Case} from 'view-utils';
import {onNextTick} from 'utils';
import {DateFilter} from './date/DateFilter';
import {VariableFilter} from './variable/VariableFilter';
import {deleteFilter as deleteFilterService} from './service';
import {ExecutedNodeFilter} from './executedNode';

export function FilterBar({onFilterDeleted}) {
  return <ul className="list-group filter-list">
    <Scope selector="filter">
      <List>
        <li className="list-group-item">
          <Match>
            <Case predicate={isType('startDate')}>
              <DateFilter selector="data" onDelete={deleteFilter}/>
            </Case>
            <Case predicate={isType('variable')}>
              <VariableFilter selector="data" onDelete={deleteFilter}/>
            </Case>
            <Case predicate={isType('executedNode')}>
              <ExecutedNodeFilter selector="data" onDelete={deleteFilter} />
            </Case>
          </Match>
        </li>
      </List>
    </Scope>
  </ul>;

  function deleteFilter({state}) {
    deleteFilterService(state);

    onNextTick(onFilterDeleted);
  }

  function isType(targetType) {
    return ({type}) => type === targetType;
  }
}
