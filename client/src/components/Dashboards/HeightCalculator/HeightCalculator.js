import React from 'react';

/**
 * Sets the height of the container to ensure that it spanes
 * to the whole space used by the reports.
 */
export default class HeightCalculator extends React.Component {
  render() {
    const {container, tileDimensions:{outerHeight}, reports} = this.props;

    const lowestReport = Math.max(0, ...reports.map(({position: {y}, dimensions: {height}}) => y + height));

    container.style.height = outerHeight * lowestReport + 'px';

    return null;
  }
}
