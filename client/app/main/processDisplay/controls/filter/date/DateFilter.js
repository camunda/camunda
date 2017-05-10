import {jsx, withSelector, Scope, Text, OnEvent} from 'view-utils';

export const DateFilter = withSelector(({onDelete}) => {
  return <span>
    <button type="button" className="btn btn-link btn-xs pull-right">
      <OnEvent event="click" listener={onDelete} />
      ×
    </button>
    <span>
      Start Date between&nbsp;
      <span className="badge">
        <Scope selector={formatDate('start')}>
          <Text property="date" />
        </Scope>
      </span>
      &nbsp;and&nbsp;
      <span className="badge">
        <Scope selector={formatDate('end')}>
          <Text property="date" />
        </Scope>
      </span>
    </span>
  </span>;

  function formatDate(prop) {
    return (state) => {
      return {
        date: state[prop].substr(0, 10)
      };
    };
  }
});
