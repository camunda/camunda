/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/**
 * Sets the height and width of the container to ensure that it spanes
 * to the whole space used by the reports.
 */
export default function DimensionSetter(props) {
  const {
    container,
    tileDimensions: {outerHeight, outerWidth, columns},
    reports
  } = props;
  const emptyRows = props.emptyRows || 0;
  const lowestReport = Math.max(
    0,
    ...reports.map(({position: {y}, dimensions: {height}}) => y + height)
  );

  const rows = emptyRows + lowestReport;

  container.style.height = outerHeight * rows + 'px';
  container.style.width = outerWidth * columns + 'px';

  return null;
}
