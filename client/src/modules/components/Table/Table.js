import React from 'react';
import classnames from 'classnames';
import ReactTable from 'react-table';

import './Table.css';

export default class Table extends React.Component {
  render() {
    const {className, head, body, disableReportScrolling} = this.props;

    const columns = Table.formatColumns(head);
    const data = Table.formatData(head, body);
    return (
      <div className={classnames('Table__container', className)} ref={ref => (this.tableRef = ref)}>
        <ReactTable
          data={data}
          columns={columns}
          defaultPageSize={Number.MAX_SAFE_INTEGER}
          showPagination={false}
          showPaginationTop={false}
          showPaginationBottom={false}
          minRows={0}
          sortable={false}
          multiSort={false}
          className={classnames('-striped', '-highlight', 'Table', {
            'Table__unscrollable-mode': disableReportScrolling
          })}
          noDataText="No data available"
        />
      </div>
    );
  }

  fixColumnAlignment = () => {
    const {clientWidth, offsetWidth} = this.tableRef.querySelector('.rt-tbody');
    const margin = clientWidth < offsetWidth ? offsetWidth - clientWidth : 0;

    this.tableRef.querySelectorAll('.rt-thead > .rt-tr').forEach(({style}) => {
      style.marginRight = margin + 'px';
    });
  };

  componentDidMount() {
    this.fixColumnAlignment();

    // on dashboards
    const resizableContainer = this.tableRef.closest('.DashboardObject');
    if (resizableContainer) {
      new MutationObserver(this.fixColumnAlignment).observe(resizableContainer, {
        attributes: true
      });
    }

    // on report page
    window.addEventListener('resize', this.fixColumnAlignment);

    // for dynamic content (e.g. targetValue modal)
    new MutationObserver(this.fixColumnAlignment).observe(this.tableRef, {
      childList: true,
      subtree: true
    });
  }

  componentWillUnmount() {
    window.removeEventListener('resize', this.fixColumnAlignment);
  }

  static formatColumns = head => {
    return head.map(elem => {
      return {
        Header: elem,
        accessor: convertHeaderNameToAccessor(elem),
        minWidth: 40
      };
    });
  };

  static formatData = (head, body) => {
    return body.map((row, rowIdx) => {
      const newRow = {};
      row.forEach((cell, columnIdx) => {
        newRow[convertHeaderNameToAccessor(head[columnIdx])] = cell;
      });
      return newRow;
    });
  };
}

function convertHeaderNameToAccessor(name) {
  return name
    .split(' ')
    .join('_')
    .toLowerCase();
}
