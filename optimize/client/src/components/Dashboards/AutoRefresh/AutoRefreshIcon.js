/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component} from 'react';
import {UpdateNow} from '@carbon/icons-react';

import './AutoRefreshIcon.scss';

const imageSize = 16;
const center = imageSize / 2;
const radius = center - 1;

export default class AutoRefreshIcon extends Component {
  constructor(props) {
    super(props);

    this.state = {
      animationStarted: Date.now(),
    };
  }

  animate = () => {
    if (this.props.interval && this.path) {
      const progress = ((Date.now() - this.state.animationStarted) / this.props.interval) % 1;
      const angle = Math.PI * (progress * 2 - 0.5);

      const endX = center + radius * Math.cos(angle);
      const endY = center + radius * Math.sin(angle);

      this.path.setAttribute(
        'd',
        `M ${center} ${center - radius} A ${radius} ${radius} 0 ${+(
          progress > 0.5
        )} 1 ${endX} ${endY}`
      );
    }

    this.rafId = requestAnimationFrame(this.animate);
  };

  componentDidMount() {
    this.rafId = requestAnimationFrame(this.animate);
  }

  componentWillUnmount() {
    cancelAnimationFrame(this.rafId);
  }

  componentDidUpdate(prevProps) {
    if (prevProps.interval !== this.props.interval) {
      this.setState({animationStarted: Date.now()});
    }
  }

  render() {
    return !this.props.interval ? (
      <UpdateNow />
    ) : (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox={`0 0 ${imageSize} ${imageSize}`}
        width={imageSize}
        height={imageSize}
        className="AutoRefreshIcon"
      >
        <circle cx={center} cy={center} r={radius} strokeWidth="0" fillOpacity="0.2" />
        <path
          strokeWidth="2"
          ref={(path) => (this.path = path)}
          className="AutoRefreshIcon__outline"
        />
      </svg>
    );
  }
}
