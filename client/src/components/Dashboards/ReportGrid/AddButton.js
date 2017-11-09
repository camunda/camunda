import React from 'react';

import './AddButton.css'

export default class ViewGrid extends React.Component {

  render () {
    const buttonTiles = this.props.buttonSize;

    const gridMargin = this.props.gridMargin;
    const gridSize = this.props.gridSize;

    const buttonStyle = {
      top: (gridSize * this.props.buttonTop) + gridMargin + 'px',
      left: (gridSize * this.props.buttonLeft) + gridMargin + 'px',
      width: gridSize * buttonTiles - gridMargin + 'px',
      height: gridSize * buttonTiles - gridMargin + 'px'
    };

    return (
      <div className={'add-button'} onClick={this.props.onClick} style={buttonStyle}>

        <div className={'add-sign-container'}>
          <div className={'add-sign'}>&#65291;</div>
        </div>
        <div className={'add-text'}>Add a report</div>
      </div>)
  }
}
