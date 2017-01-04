import {jsx, Select} from 'view-utils';
import {Filters, getFilters} from './filters';
import {Diagram} from './diagram';

export function ProcessDisplay({selector}) {
  return <div className="process-display">
    <Select selector={selector}>
      <Filters/>
      <Select selector={getDiagramState}>
        <Diagram/>
      </Select>
    </Select>
  </div>;

  function getDiagramState(state) {
    const {filters, diagram} = state;

    return {
      ...diagram,
      filters: getFilters(filters)
    };
  }
}
