import React from 'react';

import './ResizeHandle.css';
import {snapInPosition, collidesWithReport, applyPlacement} from '../service';

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

    document.body.removeEventListener('mousemove', this.saveMouseAndUpdateCardSize);
    document.body.removeEventListener('mouseup', this.stopDragging);
    window.removeEventListener('scroll', this.updateCardSize);

    const newPlacement = snapInPosition({
      tileDimensions: this.props.tileDimensions,
      report: this.props.report,
      changes: {
        width: parseInt(this.reportCard.style.width, 10) - this.cardStartSize.width,
        height: parseInt(this.reportCard.style.height, 10) - this.cardStartSize.height
      }
    });

    if(!collidesWithReport({
      placement: newPlacement,
      reports: this.props.reports.filter(report => report !== this.props.report)
    })) {
      applyPlacement({
        placement: newPlacement,
        tileDimensions: this.props.tileDimensions,
        node: this.reportCard
      });

      this.props.updateReport({
        report: this.props.report,
        dimensions: newPlacement.dimensions
      });
    } else {
      applyPlacement({
        placement: {
          position: this.props.report.position,
          dimensions: this.props.report.dimensions
        },
        tileDimensions: this.props.tileDimensions,
        node: this.reportCard
      });
    }

    this.reportCard.classList.remove('ResizeHandle--dragging');
    this.props.onResizeEnd();
  }
}
