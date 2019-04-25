/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import './ResizeHandle.scss';
import {snapInPosition, collidesWithReport, applyPlacement} from '../service';

export default class ResizeHandle extends React.Component {
  render() {
    return <div className="ResizeHandle" ref={this.storeNode} onMouseDown={this.startDragging} />;
  }

  componentDidMount() {
    this.dashboard = this.resizeHandle.closest('.DashboardRenderer');
    this.dropShadow = document.createElement('div');
    this.dropShadow.classList.add('ResizeHandle__dropShadow');

    this.dashboard.appendChild(this.dropShadow);
  }

  componentWillUnmount() {
    this.dashboard.removeChild(this.dropShadow);
  }

  storeNode = element => {
    this.resizeHandle = element;
  };

  startDragging = evt => {
    evt.preventDefault();

    this.reportCard = this.resizeHandle.closest('.DashboardObject');
    this.scrollContainer = this.resizeHandle.closest('main');

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
    this.cardStartSize = {
      width: parseInt(this.reportCard.style.width, 10),
      height: parseInt(this.reportCard.style.height, 10)
    };
    this.startScrollPosition = {
      x: this.scrollContainer.scrollLeft,
      y: this.scrollContainer.scrollTop
    };

    document.body.addEventListener('mousemove', this.saveMouseAndUpdateCardSize);
    document.body.addEventListener('mouseup', this.stopDragging);
    this.scrollContainer.addEventListener('scroll', this.updateCardSize);

    // make sure report card and its shadow are topmost elements
    this.dashboard.appendChild(this.dropShadow);
    this.reportCard.parentNode.appendChild(this.reportCard);

    this.reportCard.classList.add('ResizeHandle--dragging');
    this.dropShadow.classList.add('ResizeHandle__dropShadow--active');

    this.props.onResizeStart();
  };

  saveMouseAndUpdateCardSize = evt => {
    this.lastMousePosition.x = evt.screenX;
    this.lastMousePosition.y = evt.screenY;

    this.updateCardSize();
    this.updateDropPreview();
  };

  updateCardSize = () => {
    this.reportCard.style.width =
      Math.max(
        this.cardStartSize.width +
          this.lastMousePosition.x -
          this.mouseStartPosition.x +
          this.scrollContainer.scrollLeft -
          this.startScrollPosition.x,
        this.props.tileDimensions.innerWidth
      ) + 'px';
    this.reportCard.style.height =
      Math.max(
        this.cardStartSize.height +
          this.lastMousePosition.y -
          this.mouseStartPosition.y +
          this.scrollContainer.scrollTop -
          this.startScrollPosition.y,
        this.props.tileDimensions.innerHeight
      ) + 'px';
  };

  updateDropPreview = () => {
    const placement = snapInPosition({
      tileDimensions: this.props.tileDimensions,
      report: this.props.report,
      changes: {
        width: parseInt(this.reportCard.style.width, 10) - this.cardStartSize.width,
        height: parseInt(this.reportCard.style.height, 10) - this.cardStartSize.height
      }
    });

    applyPlacement({placement, tileDimensions: this.props.tileDimensions, node: this.dropShadow});

    if (
      collidesWithReport({
        placement,
        reports: this.props.reports.filter(report => report !== this.props.report)
      })
    ) {
      this.dropShadow.classList.add('ResizeHandle__dropShadow--invalid');
    } else {
      this.dropShadow.classList.remove('ResizeHandle__dropShadow--invalid');
    }
  };

  stopDragging = () => {
    if (!this.dragging) {
      return;
    }

    const {tileDimensions, report, reports} = this.props;

    this.dragging = false;

    document.body.removeEventListener('mousemove', this.saveMouseAndUpdateCardSize);
    document.body.removeEventListener('mouseup', this.stopDragging);
    this.scrollContainer.removeEventListener('scroll', this.updateCardSize);

    const newPlacement = snapInPosition({
      tileDimensions,
      report,
      changes: {
        width: parseInt(this.reportCard.style.width, 10) - this.cardStartSize.width,
        height: parseInt(this.reportCard.style.height, 10) - this.cardStartSize.height
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
        dimensions: newPlacement.dimensions
      });
    } else {
      const placement = {
        position: report.position,
        dimensions: report.dimensions
      };

      applyPlacement({placement, tileDimensions, node: this.reportCard});
      applyPlacement({placement, tileDimensions, node: this.dropShadow});
    }

    this.reportCard.classList.remove('ResizeHandle--dragging');
    this.dropShadow.classList.remove('ResizeHandle__dropShadow--active');
    this.props.onResizeEnd();
  };
}
