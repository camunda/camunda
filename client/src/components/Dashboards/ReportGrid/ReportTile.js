import React from 'react';

import './ReportTile.css';

export default function ReportTile (props) {

  const startDrag = () => {
    props.startDrag(props.data);
  }

  const endDrag = () => {
    props.onDragEnd(props.data);
  }


  const { gridSize, gridMargin, data} = props;

  const tileStyle = {
    top: (gridSize * data.position.x) + gridMargin + 'px',
    left: (gridSize * data.position.y) + gridMargin + 'px',
    width: gridSize * data.dimensions.width - gridMargin + 'px',
    height: gridSize * data.dimensions.height - gridMargin + 'px'
  };

  return (
    <div className={'report-tile--container'}
         onDragStart={startDrag.bind(this)}
         onDragEnd={endDrag.bind(this)}
         draggable="true"
         style={tileStyle}
    >

      <div className={'report-tile--report-name-container'}>
        <div className={'report-tile--report-name'}>{data.name}</div>
      </div>
    </div>)
}
