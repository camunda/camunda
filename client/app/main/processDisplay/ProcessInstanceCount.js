import {jsx, withSelector, Scope, Text} from 'view-utils';
import {formatNumber} from 'utils';

export const ProcessInstanceCount = withSelector(() => {
  return <div className="statistics">
    <Scope selector={formatData}>
      <div className="count"><Text property="data" /></div>
      <div className="label">Instances</div>
    </Scope>
  </div>;

  function formatData(instanceCount) {
    return {data: formatNumber(instanceCount, '\u2009')};
  }
});
