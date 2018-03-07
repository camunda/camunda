import React from 'react';

import './DragBehavior.css';
import {snapInPosition, collidesWithReport, applyPlacement} from '../service';

export default class DragBehavior extends React.Component {
  render() {
    return (
      <div
        className="DragBehavior"
        ref={this.storeNode}
        onMouseDown={this.startDragging}
        onMouseUp={this.stopDragging}
      />
    );
  }

  storeNode = element => {
    this.dragBehaviorNode = element;
  };

  startDragging = evt => {
    evt.preventDefault();

    this.reportCard = this.dragBehaviorNode.closest('.DashboardObject');
    this.scrollContainer = this.dragBehaviorNode.closest('main');

    if (this.dragging) {
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
      x: this.scrollContainer.scrollLeft,
      y: this.scrollContainer.scrollTop
    };

    document.body.addEventListener('mousemove', this.saveMouseAndUpdateCardPosition);
    this.scrollContainer.addEventListener('scroll', this.updateCardPosition);

    // make sure report card is topmost element
    this.reportCard.parentNode.appendChild(this.reportCard);

    this.reportCard.classList.add('DragBehavior--dragging');

    this.props.onDragStart();
  };

  saveMouseAndUpdateCardPosition = evt => {
    this.lastMousePosition.x = evt.screenX;
    this.lastMousePosition.y = evt.screenY;

    this.updateCardPosition();
  };

  updateCardPosition = () => {
    this.reportCard.style.left =
      this.cardStartPosition.x +
      this.lastMousePosition.x -
      this.mouseStartPosition.x +
      this.scrollContainer.scrollLeft -
      this.startScrollPosition.x +
      'px';
    this.reportCard.style.top =
      this.cardStartPosition.y +
      this.lastMousePosition.y -
      this.mouseStartPosition.y +
      this.scrollContainer.scrollTop -
      this.startScrollPosition.y +
      'px';
  };

  stopDragging = () => {
    if (!this.dragging) {
      return;
    }

    this.dragging = false;

    document.body.removeEventListener('mousemove', this.saveMouseAndUpdateCardPosition);
    this.scrollContainer.removeEventListener('scroll', this.updateCardPosition);

    const newPlacement = snapInPosition({
      tileDimensions: this.props.tileDimensions,
      report: this.props.report,
      changes: {
        x: parseInt(this.reportCard.style.left, 10) - this.cardStartPosition.x,
        y: parseInt(this.reportCard.style.top, 10) - this.cardStartPosition.y
      }
    });

    if (
      !collidesWithReport({
        placement: newPlacement,
        reports: this.props.reports.filter(report => report !== this.props.report)
      })
    ) {
      applyPlacement({
        placement: newPlacement,
        tileDimensions: this.props.tileDimensions,
        node: this.reportCard
      });

      this.props.updateReport({
        report: this.props.report,
        position: newPlacement.position
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

    this.reportCard.classList.remove('DragBehavior--dragging');
    this.props.onDragEnd();
  };
}
