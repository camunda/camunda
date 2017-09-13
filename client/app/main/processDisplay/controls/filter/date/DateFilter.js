import React from 'react';
import {createViewUtilsComponentFromReact} from 'reactAdapter';

const jsx = React.createElement;

export class DateFilterReact extends React.Component {
  render() {
    return <span>
      <button type="button" className="btn btn-link btn-xs pull-right" onClick={this.onDelete}>
        ×
      </button>
      <span>
        Start Date between&nbsp;
        <span className="badge">
          {this.formatDate('start')}
        </span>
        &nbsp;and&nbsp;
        <span className="badge">
          {this.formatDate('end')}
        </span>
      </span>
    </span>;
  }

  formatDate(prop) {
    return this.props.filter && this.props.filter[prop] && this.props.filter[prop].substr(0, 10);
  }

  onDelete = () => {
    this.props.onDelete({
      state: this.props.filter
    });
  }
}

export const DateFilter = createViewUtilsComponentFromReact('span', DateFilterReact);
