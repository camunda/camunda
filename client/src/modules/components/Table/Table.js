import React from 'react';
import classnames from 'classnames';
import ReactTable from 'react-table';

import './Table.css';

export default function Table({className, head, body, foot, scrollableReport}) {
  const columns = Table.formatColumns(head);
  const data = Table.formatData(head, body);
  return (
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
      className={classnames('-striped', '-highlight', 'Table', className, {
        'Table__unscrollable-mode': scrollableReport
      })}
      noDataText="No data available"
    />
  );
}

Table.formatColumns = head => {
  return head.map(elem => {
    return {
      Header: elem,
      accessor: convertHeaderNameToAccessor(elem),
      minWidth: 40
    };
  });
};

Table.formatData = (head, body) => {
  return body.map((row, rowIdx) => {
    const newRow = {};
    row.forEach((cell, columnIdx) => {
      newRow[convertHeaderNameToAccessor(head[columnIdx])] = cell;
    });
    return newRow;
  });
};

function convertHeaderNameToAccessor(name) {
  return name
    .split(' ')
    .join('_')
    .toLowerCase();
}
