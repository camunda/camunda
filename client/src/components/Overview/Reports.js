import React from 'react';
import {Button} from 'components';

import entityIcons from './entityIcons';
import ReportItem from './subComponents/ReportItem';
import NoEntities from './subComponents/NoEntities';
import classnames from 'classnames';

const HeaderIcon = entityIcons.report.header.Component;
const OpenCloseIcon = entityIcons.entityOpenClose;

class Reports extends React.Component {
  state = {
    open: true,
    limit: true
  };

  render() {
    const empty = this.props.reports.length === 0 && (
      <NoEntities label="Report" createFunction={this.props.createProcessReport} />
    );

    const ToggleButton = ({children}) =>
      this.props.reports.length > 0 ? (
        <Button className="ToggleCollapse" onClick={() => this.setState({open: !this.state.open})}>
          <OpenCloseIcon className={classnames('collapseIcon', {right: !this.state.open})} />
          {children}
        </Button>
      ) : (
        children
      );

    return (
      <div className="Reports">
        <div className="header">
          <ToggleButton>
            <h1>
              <HeaderIcon /> Reports
            </h1>
          </ToggleButton>
        </div>
        {this.state.open && (
          <>
            <ul className="entityList">
              {empty}
              {this.props.reports.slice(0, this.state.limit ? 5 : undefined).map(report => (
                <ReportItem
                  key={report.id}
                  report={report}
                  duplicateEntity={this.props.duplicateEntity}
                  showDeleteModalFor={this.props.showDeleteModalFor}
                  renderCollectionsDropdown={this.props.renderCollectionsDropdown}
                />
              ))}
            </ul>
            {this.props.reports.length > 5 &&
              (this.state.limit ? (
                <>
                  {this.props.reports.length} Reports.{' '}
                  <Button type="link" onClick={() => this.setState({limit: false})}>
                    Show all...
                  </Button>
                </>
              ) : (
                <Button type="link" onClick={() => this.setState({limit: true})}>
                  Show less...
                </Button>
              ))}
          </>
        )}
      </div>
    );
  }
}

export default Reports;
