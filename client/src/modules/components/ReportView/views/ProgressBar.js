import React from 'react';

import './ProgressBar.css';

export default function ProgressBar({min, max, value, formatter}) {
  const relative = Math.min(1, Math.max(0, (value - min) / (max - min)));

  return (
    <div className="ProgressBar">
      <div className="ProgressBar__label">{formatter(value)}</div>
      <div className="ProgressBar--filled" style={{width: `${relative * 100}%`}} />
      <div className="ProgressBar__range">
        {formatter(min)}
        <span className="ProgressBar__range--right">Target: {formatter(max)}</span>
      </div>
    </div>
  );
}
