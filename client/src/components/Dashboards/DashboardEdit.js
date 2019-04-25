/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';
import update from 'immutability-helper';
import {evaluateReport} from 'services';

import {DashboardRenderer, EntityNameForm} from 'components';

import {AddButton} from './AddButton';
import {Grid} from './Grid';
import {DimensionSetter} from './DimensionSetter';
import {DeleteButton} from './DeleteButton';
import {DragBehavior} from './DragBehavior';
import {ResizeHandle} from './ResizeHandle';

export default class DashboardEdit extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      reports: props.initialReports,
      addButtonVisible: true
    };
  }

  updateReport = ({report, ...changes}) => {
    const reportIdx = this.state.reports.indexOf(report);

    Object.keys(changes).forEach(prop => {
      changes[prop] = {$set: changes[prop]};
    });

    this.setState({
      reports: update(this.state.reports, {
        [reportIdx]: changes
      })
    });
  };

  showAddButton = () => {
    this.setState({
      addButtonVisible: true
    });
  };

  hideAddButton = () => {
    this.setState({
      addButtonVisible: false
    });
  };

  addReport = newReport => {
    this.setState({reports: update(this.state.reports, {$push: [newReport]})});
  };

  deleteReport = ({report: reportToRemove}) => {
    this.setState({
      reports: this.state.reports.filter(report => report !== reportToRemove)
    });
  };

  render() {
    const {name, lastModifier, lastModified, id, isNew} = this.props;

    const {reports} = this.state;

    return (
      <div className="DashboardEdit">
        <div className="header">
          <EntityNameForm
            id={id}
            initialName={name}
            lastModified={lastModified}
            lastModifier={lastModifier}
            autofocus={isNew}
            entity="Dashboard"
            onSave={(evt, newName) => this.props.saveChanges(newName, reports)}
          />
          <div className="subHead">
            <div className="metadata">
              Last modified {moment(lastModified).format('lll')} by {lastModifier}
            </div>
          </div>
        </div>
        <DashboardRenderer
          disableReportScrolling
          loadReport={evaluateReport}
          reports={reports}
          reportAddons={[
            <DragBehavior
              key="DragBehavior"
              reports={reports}
              updateReport={this.updateReport}
              onDragStart={this.hideAddButton}
              onDragEnd={this.showAddButton}
            />,
            <DeleteButton key="DeleteButton" deleteReport={this.deleteReport} />,
            <ResizeHandle
              key="ResizeHandle"
              reports={reports}
              updateReport={this.updateReport}
              onResizeStart={this.hideAddButton}
              onResizeEnd={this.showAddButton}
            />
          ]}
        >
          <Grid reports={reports} />
          <DimensionSetter emptyRows={9} reports={reports} />
          <AddButton addReport={this.addReport} visible={this.state.addButtonVisible} />
        </DashboardRenderer>
      </div>
    );
  }
}
