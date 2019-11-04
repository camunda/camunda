/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';
import update from 'immutability-helper';
import deepEqual from 'deep-equal';

import {evaluateReport} from 'services';
import {DashboardRenderer, EntityNameForm} from 'components';
import {t} from 'translation';
import {nowDirty, nowPristine} from 'saveGuard';

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
      name: props.name,
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

  updateName = ({target: {value}}) => {
    this.setState({name: value});
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

  componentDidUpdate() {
    if (
      deepEqual(this.state.reports, this.props.initialReports) &&
      this.state.name === this.props.name
    ) {
      nowPristine();
    } else {
      nowDirty(t('dashboard.label'), this.save);
    }
  }

  save = async () => {
    const {name, reports} = this.state;

    nowPristine();
    await this.props.saveChanges(name, reports);
  };

  render() {
    const {lastModifier, lastModified, isNew} = this.props;

    const {reports, name} = this.state;

    return (
      <div className="DashboardEdit">
        <div className="header">
          <EntityNameForm
            name={name}
            lastModified={lastModified}
            lastModifier={lastModifier}
            isNew={isNew}
            entity="Dashboard"
            onChange={this.updateName}
            onSave={this.save}
            onCancel={nowPristine}
          />
          <div className="subHead">
            <div className="metadata">
              {t('common.entity.modified')} {moment(lastModified).format('lll')}{' '}
              {t('common.entity.by')} {lastModifier}
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
