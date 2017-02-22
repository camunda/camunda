import {jsx, withSelector, Class, OnEvent} from 'view-utils';
import {leaveGatewayAnalysisMode} from '../diagram/analytics/service';

export const Statistics = withSelector(() => {
  return <div className="statisticsContainer">
    <Class className="open" predicate={isSelectionComplete} />
    <button type="button" className="close">
      <OnEvent event="click" listener={leaveGatewayAnalysisMode} />
      <span>×</span>
    </button>
  </div>;

  function isSelectionComplete({endEvent, gateway}) {
    return endEvent && gateway;
  }
});
