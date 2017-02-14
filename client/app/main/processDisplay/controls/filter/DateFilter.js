import {jsx, withSelector, Scope, Text} from 'view-utils';

export const DateFilter = withSelector(() => {
  return <li className="list-group-item" style="padding: 6px;">
    <button type="button" className="btn btn-link btn-xs" style="float: right;">
      ×
    </button>
    <span>
      Start Date between&nbsp;
      <span className="badge" style="float: none;">
        <Scope selector={formatDate('start')}>
          <Text property="date" />
        </Scope>
      </span>
      &nbsp;and&nbsp;
      <span className="badge" style="float: none;">
        <Scope selector={formatDate('end')}>
          <Text property="date" />
        </Scope>
      </span>
    </span>
  </li>;

  function formatDate(prop) {
    return (state) => {
      return {
        date: state[prop].substr(0, 10)
      };
    };
  }
});
