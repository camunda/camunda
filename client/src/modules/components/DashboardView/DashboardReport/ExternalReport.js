import React from 'react';

export default function ExternalReport(props) {
  const {report, disableReportScrolling, children = () => {}} = props;

  if (report.configuration && report.configuration.external) {
    return (
      <div className="DashboardReport__wrapper">
        <iframe
          title="External Report"
          src={report.configuration.external}
          frameBorder="0"
          scrolling={disableReportScrolling ? 'no' : 'yes'}
          style={{width: '100%', height: '100%'}}
        />
        {children()}
      </div>
    );
  }
}
