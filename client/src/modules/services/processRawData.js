import React from 'react';
import {convertCamelToSpaces} from './formatters';

function processDecisionRawData(data, columnOrder, endpoints, excludedColumns) {
  const instanceProps = Object.keys(data[0]).filter(
    entry =>
      entry !== 'inputVariables' && entry !== 'outputVariables' && !excludedColumns.includes(entry)
  );

  const inputVariables = Object.keys(data[0].inputVariables).filter(
    entry => !excludedColumns.includes('inp__' + entry)
  );
  const outputVariables = Object.keys(data[0].outputVariables).filter(
    entry => !excludedColumns.includes('out__' + entry)
  );

  if (instanceProps.length + inputVariables.length + outputVariables.length === 0) {
    return {head: ['No Data'], body: [['You need to enable at least one table column']]};
  }

  function applyBehavior(type, instance) {
    const content = instance[type];
    if (type === 'decisionInstanceId') {
      const {endpoint, engineName} = endpoints[instance.engineName] || {};
      if (endpoint) {
        return (
          <a href={`${endpoint}/app/cockpit/${engineName}/#/decision-instance/${content}`}>
            {content}
          </a>
        );
      }
    }
    return content;
  }

  const body = data.map(instance => {
    const propertyValues = instanceProps.map(entry => applyBehavior(entry, instance));
    const inputVariableValues = inputVariables.map(entry => {
      const value = instance.inputVariables[entry].value;
      if (value === null) {
        return '';
      }
      return value.toString();
    });
    const outputVariableValues = outputVariables.map(entry => {
      const output = instance.outputVariables[entry];
      if (output && output.values) {
        return output.values.join(', ');
      }
      return '';
    });

    return [...propertyValues, ...inputVariableValues, ...outputVariableValues];
  });

  const head = instanceProps.map(convertCamelToSpaces);

  if (inputVariables.length > 0) {
    head.push({
      label: 'Input Variables',
      columns: inputVariables.map(key => {
        const {name, id} = data[0].inputVariables[key];
        return {label: name || id, id: key};
      })
    });
  }
  if (outputVariables.length > 0) {
    head.push({
      label: 'Output Variables',
      columns: outputVariables.map(key => {
        const {name, id} = data[0].outputVariables[key];
        return {label: name || id, id: key};
      })
    });
  }

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  return {head: sortedHead, body: sortedBody};
}

export default function processRawData({
  data,
  excludedColumns = [],
  columnOrder = {instanceProps: [], variables: [], inputVariables: [], outputVariables: []},
  endpoints = {},
  reportType
}) {
  if (reportType === 'decision') {
    return processDecisionRawData(data, columnOrder, endpoints, excludedColumns);
  }

  const allColumnsLength = Object.keys(data[0]).length - 1 + Object.keys(data[0].variables).length;
  // If all columns is excluded return a message to enable one
  if (allColumnsLength === excludedColumns.length)
    return {head: ['No Data'], body: [['You need to enable at least one table column']]};

  const instanceProps = Object.keys(data[0]).filter(
    entry => entry !== 'variables' && !excludedColumns.includes(entry)
  );
  const variableNames = Object.keys(data[0].variables).filter(
    entry => !excludedColumns.includes('var__' + entry)
  );

  function applyBehavior(type, instance) {
    const content = instance[type];
    if (type === 'processInstanceId') {
      const {endpoint, engineName} = endpoints[instance.engineName] || {};
      if (endpoint) {
        return (
          <a href={`${endpoint}/app/cockpit/${engineName}/#/process-instance/${content}`}>
            {content}
          </a>
        );
      }
    }
    return content;
  }

  const body = data.map(instance => {
    let row = instanceProps.map(entry => applyBehavior(entry, instance));
    const variableValues = variableNames.map(entry => {
      const value = instance.variables[entry];
      if (value === null) {
        return '';
      }
      return value.toString();
    });
    row.push(...variableValues);

    return row;
  });

  const head = instanceProps.map(convertCamelToSpaces);

  if (variableNames.length > 0) {
    head.push({label: 'Variables', columns: variableNames});
  }

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  return {head: sortedHead, body: sortedBody};
}

function sortColumns(head, body, columnOrder) {
  const sortedHead = sortHead(head, columnOrder);
  const sortedBody = sortBody(body, head, sortedHead);

  return {sortedHead, sortedBody};
}

function sortHead(head, columnOrder) {
  const sortedHeadWithoutVariables = head
    .filter(onlyNonNestedColumns)
    .sort(byOrder(columnOrder.instanceProps));

  const sortedHeadVariables = sortNested(head, columnOrder, 'Variables', 'variables');
  const sortedHeadInputVariables = sortNested(
    head,
    columnOrder,
    'Input Variables',
    'inputVariables'
  );
  const sortedHeadOutputVariables = sortNested(
    head,
    columnOrder,
    'Output Variables',
    'outputVariables'
  );

  return [
    ...sortedHeadWithoutVariables,
    ...sortedHeadVariables,
    ...sortedHeadInputVariables,
    ...sortedHeadOutputVariables
  ];
}

function sortNested(head, columnOrder, label, accessor) {
  return head
    .filter(entry => entry.label === label)
    .map(entry => {
      return {
        ...entry,
        columns: [...entry.columns].sort(byOrder(columnOrder[accessor]))
      };
    });
}

function onlyNonNestedColumns(entry) {
  return !entry.columns;
}

function byOrder(order) {
  return function(a, b) {
    return order.indexOf(a.label || a) - order.indexOf(b.label || b);
  };
}

function sortBody(body, head, sortedHead) {
  return body.map(row => sortRow(row, head, sortedHead));
}

function sortRow(row, head, sortedHead) {
  const sortedRowWithoutVariables = row
    .filter(belongingToNonNestedColumn(head))
    .map(valueForNewColumnPosition(head, sortedHead));

  const sortedRowVariables = sortNestedRow(row, head, sortedHead, 'Variables');
  const sortedRowInputVariables = sortNestedRow(row, head, sortedHead, 'Input Variables');
  const sortedRowOutputVariables = sortNestedRow(row, head, sortedHead, 'Output Variables');

  return [
    ...sortedRowWithoutVariables,
    ...sortedRowVariables,
    ...sortedRowInputVariables,
    ...sortedRowOutputVariables
  ];
}

function sortNestedRow(row, head, sortedHead, label) {
  return row
    .filter(belongingToColumnWithLabel(head, label))
    .map(
      valueForNewColumnPosition(
        getNestedColumnsForEntryWithLabel(head, label),
        getNestedColumnsForEntryWithLabel(sortedHead, label)
      )
    );
}

function belongingToNonNestedColumn(head) {
  return function(_, idx) {
    return head[idx] && !head[idx].columns;
  };
}

function belongingToColumnWithLabel(head, label) {
  const flatHead = head.reduce(processRawData.flatten(), []);
  return function(_, idx) {
    return flatHead[idx] === label;
  };
}

function valueForNewColumnPosition(head, sortedHead) {
  return function(_, newPosition, cells) {
    const headerAtNewPosition = sortedHead[newPosition];
    const originalPosition = head.indexOf(headerAtNewPosition);

    return cells[originalPosition];
  };
}

function getNestedColumnsForEntryWithLabel(head, label) {
  const column = head.find(column => column.label === label);
  return column && column.columns;
}

processRawData.flatten = (ctx = '', suffix = () => '') => (flat, entry) => {
  if (entry.columns) {
    // nested column, flatten recursivly with augmented context
    return flat.concat(entry.columns.reduce(processRawData.flatten(ctx + entry.label, suffix), []));
  } else {
    // normal column, return current context with optional suffix
    return flat.concat(ctx + suffix(entry));
  }
};
