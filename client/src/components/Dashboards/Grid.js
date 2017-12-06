import React from 'react';

const rows = 9;

export default class Grid extends React.Component {
  render() {
    return null;
  }

  componentDidMount() {
    const {container, tileDimensions:{outerHeight, outerWidth, innerWidth, innerHeight}} = this.props;
    const margin = outerWidth - innerWidth;

    container.style.height = outerHeight * rows + 'px';

    container.style.backgroundImage = `url("data:image/svg+xml;utf8,`+
      `<svg xmlns='http://www.w3.org/2000/svg' width='${outerWidth}' height='${outerHeight}'>` +
        `<rect stroke='rgba(0, 0, 0, 0.2)' stroke-width='1' fill='none' x='${margin / 2}' y='${margin / 2}' width='${innerWidth - 1}' height='${innerHeight - 1}'/>` +
      `</svg>")`;
  }
}
