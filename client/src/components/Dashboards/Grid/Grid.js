/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

export default class Grid extends React.Component {
  render() {
    const {
      container,
      tileDimensions: {outerHeight, outerWidth, innerWidth, innerHeight}
    } = this.props;
    const margin = outerWidth - innerWidth;

    container.style.backgroundImage =
      'url("data:image/svg+xml;base64,' +
      btoa(
        `<svg xmlns='http://www.w3.org/2000/svg' width='${outerWidth}' height='${outerHeight}'>` +
          `<rect stroke='rgba(0, 0, 0, 0.2)' stroke-width='1' fill='none' x='${margin / 2 +
            1}' y='${margin / 2 + 1}' width='${innerWidth - 3}' height='${innerHeight - 3}'/>` +
          `</svg>`
      ) +
      '")';

    return null;
  }
}
