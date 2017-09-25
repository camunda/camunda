import {setHeight} from './service';
import React from 'react';

const jsx = React.createElement;

export class DragHandle extends React.PureComponent {
  mouseDown = evt => {
    evt.preventDefault();

    this.dragStart = evt.screenY;
    this.startHeight = this.props.height;

    document.addEventListener('mousemove', this.mouseMove);
    document.addEventListener('mouseup', this.mouseUp);
  }

  mouseMove = evt => {
    setHeight(this.startHeight - evt.screenY + this.dragStart);
  }

  mouseUp = evt => {
    document.removeEventListener('mousemove', this.mouseMove);
    document.removeEventListener('mouseup', this.mouseUp);

    setHeight(this.startHeight - evt.screenY + this.dragStart);
  }

  render() {
    return (
      <div className="drag-handle"
           onMouseDown={this.mouseDown} />
    );
  }
}
