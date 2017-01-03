import {jsx, dispatchAction} from 'view-utils';
import {DateFilter} from './dateFilter.component';
import {createChangeFilterAction} from './filters.reducer';

export function Filters() {
  return <div className="filters">
    <section className="filters__filter">
      <label>
        Start Date:
      </label>
      <DateFilter selector="startDate" onDateChanged={onDateChanged('startDate')}/>
    </section>

    <section className="filters__filter">
      <label>
        End Date:
      </label>
      <DateFilter selector="endDate" onDateChanged={onDateChanged('endDate')}/>
    </section>
  </div>;

  function onDateChanged(filter) {
    return (date) => dispatchAction(
      createChangeFilterAction(filter, date)
    );
  }
}
