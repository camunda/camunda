import {jsx, withSelector} from 'view-utils';
import {Diagram} from './diagram';

export const ProcessDisplay = withSelector(Process);

function Process() {
  return <div className="process-display">
    <Diagram selector="diagram"/>
  </div>;
}
