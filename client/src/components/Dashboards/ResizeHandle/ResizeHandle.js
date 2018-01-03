import React from 'react';

import './ResizeHandle.css';
import { getOccupiedTiles } from '../service';

export default class ResizeHandle extends React.Component {
  render() {
    return <div className='ResizeHandle'
      ref={this.storeNode}
      onMouseDown={this.startDragging}
    />;
  }

  storeNode = element => {
    this.resizeHandle = element;
  }

  startDragging = evt => {
    evt.preventDefault();

    this.reportCard = this.resizeHandle.closest('.DashboardObject');

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
    this.cardStartSize = {
      width: parseInt(this.reportCard.style.width, 10),
      height: parseInt(this.reportCard.style.height, 10)
    };
    this.startScrollPosition = {
      x: window.pageXOffset,
      y: window.pageYOffset
    };

    document.body.addEventListener('mousemove', this.saveMouseAndUpdateCardSize);
    document.body.addEventListener('mouseup', this.stopDragging);
    window.addEventListener('scroll', this.updateCardSize);

    // make sure report card is topmost element
    this.reportCard.parentNode.appendChild(this.reportCard);

    this.reportCard.classList.add('ResizeHandle--dragging');

    this.props.onResizeStart();
  }

  saveMouseAndUpdateCardSize = evt => {
    this.lastMousePosition.x = evt.screenX;
    this.lastMousePosition.y = evt.screenY;

    this.updateCardSize();
  }

  updateCardSize = () => {
    this.reportCard.style.width = Math.max(
      this.cardStartSize.width + this.lastMousePosition.x - this.mouseStartPosition.x + window.pageXOffset - this.startScrollPosition.x,
      this.props.tileDimensions.innerWidth
    ) + 'px';
    this.reportCard.style.height = Math.max(
      this.cardStartSize.height + this.lastMousePosition.y - this.mouseStartPosition.y + window.pageYOffset - this.startScrollPosition.y,
      this.props.tileDimensions.innerHeight
    ) + 'px';
  }

  stopDragging = () => {
    if(!this.dragging) {
      return;
    }

    this.dragging = false;
    const {columns, outerWidth, outerHeight, innerWidth} = this.props.tileDimensions;
    const margin = outerWidth - innerWidth;

    document.body.removeEventListener('mousemove', this.saveMouseAndUpdateCardSize);
    document.body.removeEventListener('mouseup', this.stopDragging);
    window.removeEventListener('scroll', this.updateCardSize);

    // -- snap in position --
    // calculate move delta
    const delta = {
      width: parseInt(this.reportCard.style.width, 10) - this.cardStartSize.width,
      height: parseInt(this.reportCard.style.height, 10) - this.cardStartSize.height
    };

    // map into tile units
    delta.width /= outerWidth;
    delta.height /= outerHeight;

    // round to next full integer values so that a report is not between tiles
    delta.width = Math.round(delta.width);
    delta.height = Math.round(delta.height);

    // get the new final position of the report in tile coordinates
    const size = {
      width: this.props.report.dimensions.width + delta.width,
      height: this.props.report.dimensions.height + delta.height
    }

    // do not allow making a report wider than the grid
    size.width = Math.min(size.width, columns - this.props.report.position.x);

    // do not allow a non-positive size
    size.width = Math.max(size.width, 1);
    size.height = Math.max(size.height, 1);

    if(!this.collidesWithOtherReport(this.props.report.position, size)) {
      // set the actual position of the dom node to the calculated snapped position
      this.reportCard.style.width = (size.width * outerWidth - margin + 1) + 'px';
      this.reportCard.style.height = (size.height * outerHeight - margin + 1) + 'px';

      // update the report
      this.props.updateReport({
        report: this.props.report,
        dimensions: size
      });
    } else {
      // reset the report to its original position
      this.reportCard.style.width = (this.props.report.dimensions.width * outerWidth - margin + 1) + 'px';
      this.reportCard.style.height = (this.props.report.dimensions.height * outerHeight - margin + 1) + 'px';
    }

    this.reportCard.classList.remove('ResizeHandle--dragging');
    this.props.onResizeEnd();
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
