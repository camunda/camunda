/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import './Overview.scss';
import Reports from './Reports';
import Dashboards from './Dashboards';
import Collections from './Collections';
import {Input, ConfirmationModal, Button, Dropdown, Icon, LoadingIndicator} from 'components';
import {StoreProvider, withStore} from './OverviewStore';

function Overview({store: {loading, deleting, conflicts, deleteLoading, searchQuery}, ...props}) {
  if (loading) {
    return <LoadingIndicator />;
  }

  return (
    <div className="Overview">
      <div className="fixed">
        <div className="header">
          <div className="searchContainer">
            <Icon className="searchIcon" type="search" />
            <Input
              required
              type="text"
              className="searchInput"
              placeholder="Filter for name..."
              value={searchQuery}
              onChange={({target: {value}}) => props.filter(value)}
            />
            <button className="searchClear" onClick={() => props.filter('')}>
              <Icon type="clear" />
            </button>
          </div>
          <div className="createAllButton">
            <Dropdown label="Create New" color="blue">
              <Dropdown.Option onClick={() => props.setCollectionToUpdate({})}>
                New Collection
              </Dropdown.Option>
              <Dropdown.Option link="/dashboard/new/edit">New Dashboard</Dropdown.Option>
              <Dropdown.Submenu label="New Report">
                <Dropdown.Option link="/report/new/edit">Process Report</Dropdown.Option>
                <Dropdown.Option link="/report/new-combined/edit">
                  Combined Process Report
                </Dropdown.Option>
                <Dropdown.Option link="/report/new-decision/edit">Decision Report</Dropdown.Option>
              </Dropdown.Submenu>
            </Dropdown>
          </div>
        </div>
      </div>
      <Collections />
      <div className="createDashboard">
        <Button tag="a" to="/dashboard/new/edit">
          Create Dashboard
        </Button>
      </div>
      <Dashboards />
      <div className="createReport">
        <Button tag="a" to="/report/new/edit">
          Create Process Report
        </Button>
        <Dropdown label={<Icon type="down" />}>
          <Dropdown.Option link="/report/new/edit">Create Process Report</Dropdown.Option>
          <Dropdown.Option link="/report/new-combined/edit">
            Create Combined Process Report
          </Dropdown.Option>
          <Dropdown.Option link="/report/new-decision/edit">Create Decision Report</Dropdown.Option>
        </Dropdown>
      </div>
      <Reports />
      <ConfirmationModal
        open={deleting !== false}
        onClose={props.hideDeleteModal}
        onConfirm={props.deleteEntity}
        entityName={deleting && deleting.entity.name}
        conflict={{type: 'Delete', items: conflicts}}
        loading={deleteLoading}
      />
    </div>
  );
}

export default props => {
  const OverviewWithStore = withStore(Overview);
  return (
    <StoreProvider>
      <OverviewWithStore {...props} />
    </StoreProvider>
  );
};
