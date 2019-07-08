/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Button} from 'components';

import entityIcons from './entityIcons';
import ReportItem from './subComponents/ReportItem';
import NoEntities from './subComponents/NoEntities';
import classnames from 'classnames';
import {withStore} from './OverviewStore';
import {filterEntitiesBySearch} from './service';

const HeaderIcon = entityIcons.report.header.Component;
const OpenCloseIcon = entityIcons.entityOpenClose;

class Reports extends React.Component {
  state = {
    open: true,
    limit: true
  };

  render() {
    const {reports, searchQuery} = this.props.store;
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

    const filteredReports = filterEntitiesBySearch(reports, searchQuery);

    const noSearchResult = !empty && filteredReports.length === 0 && (
      <p className="empty">No Reports matching '{searchQuery}'</p>
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
              {noSearchResult}
              {filteredReports
                .slice(0, this.state.limit && !searchQuery ? 5 : undefined)
                .map(report => (
                  <ReportItem
                    key={report.id}
                    report={report}
                    duplicateEntity={this.props.duplicateEntity}
                    showDeleteModalFor={this.props.showDeleteModalFor}
                  />
                ))}
            </ul>
            <div className="showAll">
              {filteredReports.length > 5 &&
                !searchQuery &&
                (this.state.limit ? (
                  <>
                    {reports.length} Reports.{' '}
                    <Button variant="link" onClick={() => this.setState({limit: false})}>
                      Show all...
                    </Button>
                  </>
                ) : (
                  <Button variant="link" onClick={() => this.setState({limit: true})}>
                    Show less...
                  </Button>
                ))}
            </div>
          </>
        )}
      </div>
    );
  }
}

export default withStore(Reports);
