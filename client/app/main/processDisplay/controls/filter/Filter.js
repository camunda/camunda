import {jsx, Scope, DESTROY_EVENT} from 'view-utils';
import {FilterBar} from './FilterBar';
import {CreateFilter} from './CreateFilter';
import {getFilter} from './service';
import {onHistoryStateChange} from './store';
import {ControlsElement} from '../ControlsElement';

export const Filter = ({onFilterChanged, getProcessDefinition, getDiagramXML}) => {
  const template = <Scope selector={createFilterState}>
    <ControlsElement name="Filter">
      <div className="filter">
        <FilterBar onFilterDeleted={onFilterChanged}  />
        <CreateFilter onFilterAdded={onFilterChanged} getProcessDefinition={getProcessDefinition} getDiagramXML={getDiagramXML}  />
      </div>
    </ControlsElement>
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
