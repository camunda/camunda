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
              placeholder="Find..."
              value={searchQuery}
              onChange={({target: {value}}) => props.filter(value)}
            />
            <button className="searchClear" onClick={() => props.filter('')}>
              <Icon type="clear" />
            </button>
          </div>
          <div className="createAllButton">
            <Dropdown label="Create New">
              <Dropdown.Option onClick={() => props.setCollectionToUpdate({})}>
                New Collection
              </Dropdown.Option>
              <Dropdown.Option onClick={props.createDashboard}>New Dashboard</Dropdown.Option>
              <Dropdown.Submenu label="New Report">
                <Dropdown.Option onClick={props.createProcessReport}>
                  Process Report
                </Dropdown.Option>
                <Dropdown.Option onClick={props.createCombinedReport}>
                  Combined Process Report
                </Dropdown.Option>
                <Dropdown.Option onClick={props.createDecisionReport}>
                  Decision Report
                </Dropdown.Option>
              </Dropdown.Submenu>
            </Dropdown>
          </div>
        </div>
      </div>
      <Collections />
      <div className="createDashboard">
        <Button onClick={props.createDashboard}>Create Dashboard</Button>
      </div>
      <Dashboards />
      <div className="createReport">
        <Button onClick={props.createProcessReport}>Create Process Report</Button>
        <Dropdown label={<Icon type="down" />}>
          <Dropdown.Option onClick={props.createProcessReport}>
            Create Process Report
          </Dropdown.Option>
          <Dropdown.Option onClick={props.createCombinedReport}>
            Create Combined Process Report
          </Dropdown.Option>
          <Dropdown.Option onClick={props.createDecisionReport}>
            Create Decision Report
          </Dropdown.Option>
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
