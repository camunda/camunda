import React from 'react';

export default function DashboardView({children, reportAddons}) {
  return (
    <div className="DashboardView">
      {children} Addons: {reportAddons}
    </div>
  );
}
