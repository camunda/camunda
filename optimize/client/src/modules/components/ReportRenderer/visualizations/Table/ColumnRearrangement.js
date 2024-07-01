/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import './ColumnRearrangement.scss';

export default class ColumnRearrangement extends React.Component {
  render() {
    if (!this.props.enabled) {
      return this.props.children;
    }

    return (
      <div className="ColumnRearrangement" onMouseDown={this.handleMouseDown}>
        {this.props.children}
      </div>
    );
  }

  handleMouseDown = (evt) => {
    if (evt.target.classList.contains('resizer')) {
      return;
    }

    const columnHeader = evt.target.closest('.Table th.tableHeader');

    if (columnHeader) {
      const group = getGroup(columnHeader);
      this.dragIdx = getIndex(group || columnHeader);

      const subColumnsIndexes = group ? getSubColumnIndexes(group) : [this.dragIdx];
      subColumnsIndexes.forEach((idx) => {
        forColumn(idx)
          .do(({classList}) => classList.add('ColumnRearrangement__draggedColumn'))
          .usingEvent(evt);
      });

      this.preview = createDragPreview(subColumnsIndexes[0], evt);

      document.addEventListener('mousemove', this.handleMouseMove);
      document.addEventListener('mouseup', this.handleMouseUp);
    }
  };

  handleMouseMove = (evt) => {
    if (!this.preview.parentNode) {
      document.body.appendChild(this.preview);
    }

    removeHighlights();

    const targetIdx = this.processDrag(evt);

    if (typeof targetIdx !== 'undefined') {
      forColumn(targetIdx)
        .do(({classList}) => classList.add(`ColumnRearrangement__dropTarget--left`))
        .usingEvent(evt);
      forColumn(targetIdx + 1)
        .do(({classList}) => classList.add(`ColumnRearrangement__dropTarget--right`))
        .usingEvent(evt);
    }
  };

  handleMouseUp = (evt) => {
    removeHighlights(true);

    const targetIdx = this.processDrag(evt);

    if (typeof targetIdx !== 'undefined') {
      this.props.onChange(this.dragIdx, getGroupIdx(evt, targetIdx));
    }

    document.removeEventListener('mousemove', this.handleMouseMove);
    document.removeEventListener('mouseup', this.handleMouseUp);
  };

  processDrag = (evt) => {
    const cellOrHeader = evt.target.closest('.Table td, .Table th.tableHeader');
    if (!cellOrHeader) {
      return;
    }

    const group = getGroup(cellOrHeader);

    if (group) {
      const subColumnsIndexes = getSubColumnIndexes(group);
      const mouseOffset = evt.clientX - group.getBoundingClientRect().left;

      if (mouseOffset < group.clientWidth / 2) {
        return subColumnsIndexes[0] - 1;
      }

      return subColumnsIndexes[subColumnsIndexes.length - 1];
    }

    const elemIndex = getIndex(cellOrHeader);

    if (evt.offsetX < cellOrHeader.clientWidth / 2) {
      return elemIndex - 1;
    }

    return elemIndex;
  };
}

function getSubColumnIndexes(group) {
  return Array.from(
    group
      .closest('.Table')
      .querySelectorAll(`thead tr:not(.groupRow) th.tableHeader[data-group="${getIndex(group)}"]`)
  ).map(getIndex);
}

function getIndex(el) {
  return Array.from(el.parentNode.childNodes).indexOf(el);
}

function getHeader(el, idx, groupHeader = false) {
  return el
    .closest('.Table')
    .querySelector(
      `tr${groupHeader ? '.groupRow' : ':not(.groupRow)'} th.tableHeader:nth-child(${idx + 1})`
    );
}

function getGroupIdx(evt, idx) {
  const headerElem = getHeader(evt.target, idx);
  const groupIdx = headerElem?.dataset.group;
  return typeof groupIdx !== 'undefined' ? Number(groupIdx) : idx;
}

function getGroup(cellOrHeader) {
  if (cellOrHeader.closest('.groupRow')) {
    return cellOrHeader;
  }

  const elemIndex = getIndex(cellOrHeader);
  const headerOfCell = getHeader(cellOrHeader, elemIndex);
  const groupIdx = headerOfCell?.dataset.group;
  const groupOfHeader =
    typeof groupIdx !== 'undefined' ? getHeader(cellOrHeader, Number(groupIdx), true) : null;
  return groupOfHeader;
}

function forColumn(columnIdx) {
  return {
    do: (fct) => {
      if (columnIdx === 'all') {
        cellsForColumn(document, '1n').forEach(fct);
        document.querySelectorAll('.groupRow th.tableHeader').forEach(fct);
      } else {
        return {
          usingEvent: (evt) => {
            const table = evt.target.closest('.Table');

            if (table) {
              const groupIdx = getGroupIdx(evt, columnIdx);
              const group = getHeader(evt.target, groupIdx, true);
              if (group) {
                fct(group);
              }
              cellsForColumn(table, columnIdx + 1).forEach(fct);
            }
          },
        };
      }
    },
  };
}

function cellsForColumn(target, matcher) {
  return target.querySelectorAll(
    `.Table tbody tr td:nth-child(${matcher}),.Table thead tr:not(.groupRow) th.tableHeader:nth-child(${matcher})`
  );
}

function removeHighlights(alsoRemoveDraggedColumnStyle) {
  forColumn('all').do(({classList}) =>
    [
      'ColumnRearrangement__dropTarget--left',
      'ColumnRearrangement__dropTarget--right',
      alsoRemoveDraggedColumnStyle && 'ColumnRearrangement__draggedColumn',
    ].forEach((cssClass) => classList.remove(cssClass))
  );
}

function createDragPreview(idx, evt) {
  // create a copy of the table, hide all irrelevant stuff so that only the column remains
  // then make this column follow mouse movements
  const preview = evt.target.closest('.Table').cloneNode(true);
  preview.classList.add('ColumnRearrangement__dragPreview');
  preview
    .querySelectorAll(
      `thead tr.groupRow th.tableHeader:not(:nth-child(${getGroupIdx(evt, idx) + 1}))`
    )
    .forEach(({style}) => (style.display = 'none'));
  cellsForColumn(preview, `-n+${idx}`).forEach(({style}) => (style.display = 'none'));
  cellsForColumn(preview, idx + 1).forEach(({style}) => {
    style.width = '250px';
    style.maxWidth = '250px';
  });
  cellsForColumn(preview, `n+${idx + 2}`).forEach(({style}) => (style.display = 'none'));

  preview.style.top = evt.pageY + 'px';
  preview.style.left = evt.pageX + 'px';

  function update(evt) {
    preview.style.top = evt.pageY + 'px';
    preview.style.left = evt.pageX + 'px';
  }

  function stopDrag() {
    if (preview.parentNode === document.body) {
      document.body.removeChild(preview);
    }
    document.body.removeEventListener('mousemove', update);
    document.body.removeEventListener('mouseup', stopDrag);
  }

  document.body.addEventListener('mousemove', update);
  document.body.addEventListener('mouseup', stopDrag);

  return preview;
}
