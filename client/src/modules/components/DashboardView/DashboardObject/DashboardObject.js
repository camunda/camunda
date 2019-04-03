/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import './DashboardObject.scss';

export default function DashboardObject({
  x,
  y,
  width,
  height,
  tileDimensions: {outerWidth, innerWidth, outerHeight},
  children
}) {
  const margin = outerWidth - innerWidth;

  return (
    <section
      className="DashboardObject"
      style={{
        top: y * outerHeight + margin / 2 - 1,
        left: x * outerWidth + margin / 2 - 1,
        width: width * outerWidth - margin + 1,
        height: height * outerHeight - margin + 1
      }}
    >
      {children}
    </section>
  );
}
