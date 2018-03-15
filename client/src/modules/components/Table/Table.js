import React from 'react';

import ReactTable from 'react-table';
import 'react-table/react-table.css';

import './Table.css';

export default function Table({className, head, body, foot}) {
  const columns = Table.formatColumns(head);
  const data = Table.formatData(head, body);
  return (
    <ReactTable
      data={data}
      columns={columns}
      defaultPageSize={20}
      showPagination={false}
      showPaginationTop={false}
      showPaginationBottom={false}
      minRows={0}
      sortable={false}
      multiSort={false}
      className={'-striped -highlight ' + className}
    />
  );
}

Table.formatColumns = head => {
  return head.map(elem => {
    return {
      Header: elem,
      accessor: convertHeaderNameToAccessor(elem)
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
