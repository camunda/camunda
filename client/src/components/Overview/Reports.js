import React from 'react';
import {Button} from 'components';

import entityIcons from './entityIcons';
import ReportItem from './subComponents/ReportItem';
import NoEntities from './subComponents/NoEntities';
import classnames from 'classnames';
import {withStore} from './OverviewStore';

const HeaderIcon = entityIcons.report.header.Component;
const OpenCloseIcon = entityIcons.entityOpenClose;

class Reports extends React.Component {
  state = {
    open: true,
    limit: true
  };

  render() {
    const {reports} = this.props.store;
    const empty = reports.length === 0 && (
      <NoEntities label="Report" createFunction={this.props.createProcessReport} />
    );

    const ToggleButton = ({children}) =>
      !empty ? (
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
              {reports.slice(0, this.state.limit ? 5 : undefined).map(report => (
                <ReportItem
                  key={report.id}
                  report={report}
                  duplicateEntity={this.props.duplicateEntity}
                  showDeleteModalFor={this.props.showDeleteModalFor}
                />
              ))}
            </ul>
            {reports.length > 5 &&
              (this.state.limit ? (
                <>
                  {reports.length} Reports.{' '}
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

export default withStore(Reports);
