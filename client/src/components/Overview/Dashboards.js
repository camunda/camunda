/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Button} from 'components';

import entityIcons from './entityIcons';
import DashboardItem from './subComponents/DashboardItem';
import NoEntities from './subComponents/NoEntities';
import classnames from 'classnames';
import {withStore} from './OverviewStore';
import {filterEntitiesBySearch} from './service';
import {t} from 'translation';

const HeaderIcon = entityIcons.dashboard.header.Component;
const OpenCloseIcon = entityIcons.entityOpenClose;

class Dashboards extends React.Component {
  state = {
    open: true,
    limit: true
  };

  render() {
    const {dashboards, searchQuery} = this.props.store;
    const empty = dashboards.length === 0 && (
      <NoEntities label="Dashboard" link="dashboard/new/edit" />
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

    const filteredDashboards = filterEntitiesBySearch(dashboards, searchQuery);

    const noSearchResult = !empty && filteredDashboards.length === 0 && (
      <p className="empty">{t('home.dashboard.noSearchResult', {searchQuery})}</p>
    );

    return (
      <div className="Dashboards">
        <div className="header">
          <ToggleButton>
            <h1>
              <HeaderIcon /> {t('dashboard.label-plural')}
            </h1>
          </ToggleButton>
        </div>
        {this.state.open && (
          <>
            <ul className="entityList">
              {empty}
              {noSearchResult}
              {filteredDashboards
                .slice(0, this.state.limit && !searchQuery ? 5 : undefined)
                .map(dashboard => (
                  <DashboardItem
                    key={dashboard.id}
                    dashboard={dashboard}
                    duplicateEntity={this.props.duplicateEntity}
                    showDeleteModalFor={this.props.showDeleteModalFor}
                  />
                ))}
            </ul>
            <div className="showAll">
              {filteredDashboards.length > 5 &&
                !searchQuery &&
                (this.state.limit ? (
                  <>
                    {dashboards.length} {t('dashboard.label-plural')}.{' '}
                    <Button variant="link" onClick={() => this.setState({limit: false})}>
                      {t('common.entity.showAll')}
                    </Button>
                  </>
                ) : (
                  <Button variant="link" onClick={() => this.setState({limit: true})}>
                    {t('common.entity.showLess')}
                  </Button>
                ))}
            </div>
          </>
        )}
      </div>
    );
  }
}

export default withStore(Dashboards);
