/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Icon} from 'components';

import './AutoRefreshIcon.scss';

const imageSize = 16;
const center = imageSize / 2;
const radius = center - 1;

export default class AutoRefreshIcon extends React.Component {
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

  render = () =>
    !this.props.interval ? (
      <Icon type="autorefresh" />
    ) : (
      <Icon>
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox={`0 0 ${imageSize} ${imageSize}`}
          className="AutoRefreshIcon"
        >
          <circle cx={center} cy={center} r={radius} strokeWidth="0" fillOpacity="0.2" />
          <path
            strokeWidth="2"
            ref={(path) => (this.path = path)}
            className="AutoRefreshIcon__outline"
          />
        </svg>
      </Icon>
    );
}
