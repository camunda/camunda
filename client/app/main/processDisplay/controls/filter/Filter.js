import {jsx, Scope, DESTROY_EVENT} from 'view-utils';
import {FilterBar} from './FilterBar';
import {CreateFilter} from './CreateFilter';
import {getFilter} from './service';
import {onHistoryStateChange} from './store';

export const Filter = ({onFilterChanged}) => {
  const template = <Scope selector={createFilterState}>
    <FilterBar onFilterDeleted={onFilterChanged}  />
    <CreateFilter onFilterAdded={onFilterChanged}  />
  </Scope>;

  return (node, eventsBus) => {
    const removeHistoryListener = onHistoryStateChange(onFilterChanged);

    eventsBus.on(DESTROY_EVENT, removeHistoryListener);

    return template(node, eventsBus);
  };

  function createFilterState(state) {
    const filter = getFilter();

    return {
      ...state,
      filter
    };
  }
};
