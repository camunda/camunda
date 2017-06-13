import {jsx, Scope, Text} from 'view-utils';
import {formatNumber} from 'utils';
import {getInstanceCount} from './selectors';

export const ProcessInstanceCount = () => {
  return <div className="statistics">
    <Scope selector={formatData}>
      <div className="count"><Text property="data" /></div>
      <div className="label">Instances</div>
    </Scope>
  </div>;

  function formatData(state) {
    const instanceCount = getInstanceCount(state);

    return {data: formatNumber(instanceCount)};
  }
};
