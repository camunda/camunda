/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import './DragBehavior.scss';
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

  componentDidMount() {
    this.dashboard = this.dragBehaviorNode.closest('.DashboardRenderer');
    this.dropShadow = document.createElement('div');
    this.dropShadow.classList.add('DragBehavior__dropShadow');

    this.dashboard.appendChild(this.dropShadow);
  }

  componentWillUnmount() {
    this.dashboard.removeChild(this.dropShadow);
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

    // make sure report card and its shadow are topmost elements
    this.dashboard.appendChild(this.dropShadow);
    this.reportCard.parentNode.appendChild(this.reportCard);

    this.reportCard.classList.add('DragBehavior--dragging');
    this.dropShadow.classList.add('DragBehavior__dropShadow--active');

    this.props.onDragStart();
  };

  saveMouseAndUpdateCardPosition = evt => {
    this.lastMousePosition.x = evt.screenX;
    this.lastMousePosition.y = evt.screenY;

    this.updateCardPosition();
    this.updateDropPreview();
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

  updateDropPreview = () => {
    const placement = snapInPosition({
      tileDimensions: this.props.tileDimensions,
      report: this.props.report,
      changes: {
        x: parseInt(this.reportCard.style.left, 10) - this.cardStartPosition.x,
        y: parseInt(this.reportCard.style.top, 10) - this.cardStartPosition.y
      }
    });

    applyPlacement({placement, tileDimensions: this.props.tileDimensions, node: this.dropShadow});

    if (
      collidesWithReport({
        placement,
        reports: this.props.reports.filter(report => report !== this.props.report)
      })
    ) {
      this.dropShadow.classList.add('DragBehavior__dropShadow--invalid');
    } else {
      this.dropShadow.classList.remove('DragBehavior__dropShadow--invalid');
    }
  };

  stopDragging = () => {
    if (!this.dragging) {
      return;
    }

    const {tileDimensions, report, reports} = this.props;

    this.dragging = false;

    document.body.removeEventListener('mousemove', this.saveMouseAndUpdateCardPosition);
    this.scrollContainer.removeEventListener('scroll', this.updateCardPosition);

    const newPlacement = snapInPosition({
      tileDimensions,
      report,
      changes: {
        x: parseInt(this.reportCard.style.left, 10) - this.cardStartPosition.x,
        y: parseInt(this.reportCard.style.top, 10) - this.cardStartPosition.y
      }
    });

    if (
      !collidesWithReport({
        placement: newPlacement,
        reports: reports.filter(report => report !== this.props.report)
      })
    ) {
      applyPlacement({placement: newPlacement, tileDimensions, node: this.reportCard});

      this.props.updateReport({
        report,
        position: newPlacement.position
      });
    } else {
      const placement = {
        position: report.position,
        dimensions: report.dimensions
      };

      applyPlacement({placement, tileDimensions, node: this.reportCard});
      applyPlacement({placement, tileDimensions, node: this.dropShadow});
    }

    this.reportCard.classList.remove('DragBehavior--dragging');
    this.dropShadow.classList.remove('DragBehavior__dropShadow--active');
    this.props.onDragEnd();
  };
}
