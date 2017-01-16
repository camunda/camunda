import {jsx, withSelector} from 'view-utils';
import {Filters, getFilters} from './filters';
import {Diagram} from './diagram';

export const ProcessDisplay = withSelector(Process);

function Process() {
  const DiagramWithSelector = withSelector(Diagram);

  return <div className="process-display">
    <Filters/>
    <DiagramWithSelector selector={getDiagramState} />
  </div>;

  function getDiagramState(state) {
    const {filters, diagram} = state;

    return {
      ...diagram,
      filters: getFilters(filters)
    };
  }
}
