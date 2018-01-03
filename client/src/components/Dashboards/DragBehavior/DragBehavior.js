import React from 'react';

import './DragBehavior.css';
import { getOccupiedTiles } from '../service';

export default class DragBehavior extends React.Component {
  render() {
    return <div className='DragBehavior'
      ref={this.storeNode}
      onMouseDown={this.startDragging}
      onMouseUp={this.stopDragging}
    />;
  }

  storeNode = element => {
    this.dragBehaviorNode = element;
  }

  startDragging = evt => {
    evt.preventDefault();

    this.reportCard = this.dragBehaviorNode.closest('.DashboardObject');

    if(this.dragging) {
      // do not reset previous drag values in case drag-end did not register
      return;
    }

    this.dragging = true;
    this.mouseStartPosition = {
      x: evt.screenX,
      y: evt.screenY
    };
    this.lastMousePosition = {
      x: evt.screenX,
      y: evt.screenY
    };
    this.cardStartPosition = {
      x: parseInt(this.reportCard.style.left, 10),
      y: parseInt(this.reportCard.style.top, 10)
    };
    this.startScrollPosition = {
      x: window.pageXOffset,
      y: window.pageYOffset
    };

    document.body.addEventListener('mousemove', this.saveMouseAndUpdateCardPosition);
    window.addEventListener('scroll', this.updateCardPosition);

    // make sure report card is topmost element
    this.reportCard.parentNode.appendChild(this.reportCard);

    this.reportCard.classList.add('DragBehavior--dragging');

    this.props.onDragStart();
  }

  saveMouseAndUpdateCardPosition = evt => {
    this.lastMousePosition.x = evt.screenX;
    this.lastMousePosition.y = evt.screenY;

    this.updateCardPosition();
  }

  updateCardPosition = () => {
    this.reportCard.style.left = this.cardStartPosition.x + this.lastMousePosition.x - this.mouseStartPosition.x + window.pageXOffset - this.startScrollPosition.x + 'px';
    this.reportCard.style.top = this.cardStartPosition.y + this.lastMousePosition.y - this.mouseStartPosition.y + window.pageYOffset - this.startScrollPosition.y + 'px';
  }

  stopDragging = () => {
    if(!this.dragging) {
      return;
    }

    this.dragging = false;
    const {columns, outerWidth, outerHeight, innerWidth} = this.props.tileDimensions;
    const margin = outerWidth - innerWidth;

    document.body.removeEventListener('mousemove', this.saveMouseAndUpdateCardPosition);
    window.removeEventListener('scroll', this.updateCardPosition);

    // -- snap in position --
    // calculate move delta
    const delta = {
      x: parseInt(this.reportCard.style.left, 10) - this.cardStartPosition.x,
      y: parseInt(this.reportCard.style.top, 10) - this.cardStartPosition.y
    };

    // map into tile units
    delta.x /= outerWidth;
    delta.y /= outerHeight;

    // round to next full integer values so that a report is not between tiles
    delta.x = Math.round(delta.x);
    delta.y = Math.round(delta.y);

    // get the new final position of the report in tile coordinates
    const position = {
      x: this.props.report.position.x + delta.x,
      y: this.props.report.position.y + delta.y
    }

    // do not allow placing a report outside of the grid boundaries
    position.x = Math.max(position.x, 0);
    position.x = Math.min(position.x, columns - this.props.report.dimensions.width);
    position.y = Math.max(position.y, 0);

    if(!this.collidesWithOtherReport(position, this.props.report.dimensions)) {
      // set the actual position of the dom node to the calculated snapped position
      this.reportCard.style.left = (position.x * outerWidth + margin / 2 - 1) + 'px';
      this.reportCard.style.top = (position.y * outerHeight + margin / 2 - 1) + 'px';

      // update the report
      this.props.updateReport({
        report: this.props.report,
        position
      });
    } else {
      // reset the report to its original position
      this.reportCard.style.left = (this.props.report.position.x * outerWidth + margin / 2 - 1) + 'px';
      this.reportCard.style.top = (this.props.report.position.y * outerHeight + margin / 2 - 1) + 'px';
    }

    this.reportCard.classList.remove('DragBehavior--dragging');
    this.props.onDragEnd();
  }

  collidesWithOtherReport = ({x: left, y: top}, size) => {
    const occupiedTiles = getOccupiedTiles(this.props.reports.filter(report => report !== this.props.report));

    for(let x = left; x < left + size.width; x++) {
      for(let y = top; y < top + size.height; y++) {
        if(occupiedTiles[x] && occupiedTiles[x][y]) {
          return true;
        }
      }
    }

    return false;
  }
}
