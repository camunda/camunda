import React from 'react';

import './DashboardObject.css';

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
