import {jsx, Scope, List, Match, Case} from 'view-utils';
import {onNextTick} from 'utils';
import {DateFilter} from './DateFilter';
import {deleteFilter as deleteFilterService} from './service';

export function Filter({onFilterDeleted}) {
  return <td>
    <ul className="list-group filter-list">
      <Scope selector="filter">
        <List>
          <li className="list-group-item">
            <Match>
              <Case predicate={isType('startDate')}>
                <DateFilter selector="data" onDelete={deleteFilter}/>
              </Case>
            </Match>
          </li>
        </List>
      </Scope>
    </ul>
  </td>;

  function deleteFilter({state}) {
    deleteFilterService(state);

    onNextTick(onFilterDeleted);
  }

  function isType(targetType) {
    return ({type}) => type === targetType;
  }
}
