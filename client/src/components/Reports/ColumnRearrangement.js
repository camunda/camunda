import React from 'react';

import {processRawData} from 'services';

import './ColumnRearrangement.css';

export default class ColumnRearrangement extends React.Component {
  render() {
    return (
      <div className="ColumnRearrangement" onMouseDown={this.handleMouseDown}>
        {this.props.children}
      </div>
    );
  }

  handleMouseDown = evt => {
    const columnHeader = evt.target.closest('.rt-resizable-header');

    if (columnHeader) {
      this.dragIdx = Array.from(columnHeader.parentNode.childNodes).indexOf(columnHeader);
      forColumn(this.dragIdx)
        .do(({classList}) => classList.add('ColumnRearrangement__draggedColumn'))
        .usingEvent(evt);

      const {result, data} = this.props.report;
      const currentHead = processRawData(
        result,
        data.configuration.excludedColumns,
        data.configuration.columnOrder
      ).head;

      this.columnGroups = flatten(currentHead.map(toColumnGroup));
      this.dragGroup = this.columnGroups[this.dragIdx];

      createDragPreview(this.dragIdx, evt);

      document.addEventListener('mousemove', this.handleMouseMove);
      document.addEventListener('mouseup', this.handleMouseUp);
    }
  };

  processDrag = ({evt, validTarget = () => {}, invalidTarget = () => {}}) => {
    const elem = evt.target.closest('.rt-td,.rt-resizable-header');

    if (elem) {
      let idx = Array.from(elem.parentNode.childNodes).indexOf(elem);

      if (evt.offsetX < elem.clientWidth / 2) {
        idx--;
      }

      if (
        this.dragGroup === this.columnGroups[idx] ||
        this.dragGroup === this.columnGroups[idx + 1]
      ) {
        validTarget(idx, elem);
      } else {
        invalidTarget(idx, elem);
      }
    }
  };

  handleMouseMove = evt => {
    removeHighlights();

    const applyClass = (modifier = '') => idx => {
      forColumn(idx)
        .do(({classList}) => classList.add(`ColumnRearrangement__dropTarget--${modifier}left`))
        .usingEvent(evt);
      forColumn(idx + 1)
        .do(({classList}) => classList.add(`ColumnRearrangement__dropTarget--${modifier}right`))
        .usingEvent(evt);
    };

    this.processDrag({
      evt,
      validTarget: applyClass(),
      invalidTarget: applyClass('invalid-')
    });
  };

  handleMouseUp = evt => {
    removeHighlights(true);

    this.processDrag({
      evt,
      validTarget: (idx, dropElem) => {
        // create a list of the text of all header columns
        const list = Array.from(
          dropElem.closest('.rt-table').querySelector('.rt-thead.-header .rt-tr').childNodes
        ).map(({textContent}) => textContent);

        // add the column at the specified position
        list.splice(idx + 1, 0, list[this.dragIdx]);

        // remove the original column
        list.splice(this.dragIdx + (this.dragIdx > idx), 1);

        this.props.onChange(
          list.reduce(
            (orders, entry, idx) => {
              if (this.columnGroups[idx]) {
                // if column belongs to group 1, it is a variable column
                orders.variables.push(entry);
              } else {
                // otherwise, it is not a variable column
                orders.meta.push(entry);
              }
              return orders;
            },
            {meta: [], variables: []}
          )
        );
      }
    });

    document.removeEventListener('mousemove', this.handleMouseMove);
    document.removeEventListener('mouseup', this.handleMouseUp);
  };
}

function forColumn(columnIdx) {
  return {
    do: fct => {
      if (columnIdx === 'all') {
        cellsForColumn(document, '1n').forEach(fct);
      } else {
        return {
          usingEvent: ({target}) => {
            const table = target.closest('.rt-table');

            if (table) {
              cellsForColumn(table, columnIdx + 1).forEach(fct);
            }
          }
        };
      }
    }
  };
}

function cellsForColumn(target, matcher) {
  return target.querySelectorAll(
    `.rt-tbody .rt-tr .rt-td:nth-child(${matcher}),.rt-resizable-header:nth-child(${matcher})`
  );
}

function removeHighlights(alsoRemoveDraggedColumnStyle) {
  forColumn('all').do(({classList}) =>
    classList.remove(
      'ColumnRearrangement__dropTarget--left',
      'ColumnRearrangement__dropTarget--right',
      'ColumnRearrangement__dropTarget--invalid-left',
      'ColumnRearrangement__dropTarget--invalid-right',
      alsoRemoveDraggedColumnStyle && 'ColumnRearrangement__draggedColumn'
    )
  );
}

function createDragPreview(idx, evt) {
  // create a copy of the table, hide all irrelevant stuff so that only the column remains
  // then make this column follow mouse movements
  const preview = evt.target.closest('.ReactTable').cloneNode(true);
  preview.classList.add('ColumnRearrangement__dragPreview');

  cellsForColumn(preview, `-n+${idx}`).forEach(({style}) => (style.display = 'none'));
  cellsForColumn(preview, idx + 1).forEach(({style}) => {
    style.width = '250px';
    style.maxWidth = '250px';
  });
  cellsForColumn(preview, `n+${idx + 2}`).forEach(({style}) => (style.display = 'none'));

  preview.style.top = evt.pageY + 'px';
  preview.style.left = evt.pageX + 'px';

  document.body.appendChild(preview);

  function update(evt) {
    preview.style.top = evt.pageY + 'px';
    preview.style.left = evt.pageX + 'px';
  }

  function stopDrag() {
    document.body.removeChild(preview);
    document.body.removeEventListener('mousemove', update);
    document.body.removeEventListener('mouseup', stopDrag);
  }

  document.body.addEventListener('mousemove', update);
  document.body.addEventListener('mouseup', stopDrag);
}

function toColumnGroup(column) {
  if (column.columns) {
    // nested column, map entries to column group 1
    return column.columns.map(() => 1);
  } else {
    // non-nested column is in column group 0
    return 0;
  }
}

function flatten(nestedArray) {
  return [].concat(...nestedArray);
}
