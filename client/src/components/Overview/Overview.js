import React from 'react';

import './Overview.scss';
import Reports from './Reports';
import Dashboard from './Dashboards';

export default function Overview() {
  return (
    <div className="Overview">
      <Dashboard />
      <Reports />
    </div>
  );
}
