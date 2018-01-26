import React from 'react';

/**
 * Sets the height and width of the container to ensure that it spanes
 * to the whole space used by the reports.
 */
export default class DimensionSetter extends React.Component {
  render() {
    const {container, tileDimensions:{outerHeight, outerWidth, columns}, reports} = this.props;
    const lowestReport = Math.max(0, ...reports.map(({position: {y}, dimensions: {height}}) => y + height));

    const rows = this.props.emptyRows + lowestReport;

    container.style.height = outerHeight * rows + 'px';
    container.style.width = outerWidth * columns + 'px';

    return null;
  }
}
