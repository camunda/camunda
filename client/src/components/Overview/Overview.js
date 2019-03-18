import React from 'react';

import './Overview.scss';
import Reports from './Reports';
import Dashboards from './Dashboards';
import Collections from './Collections';
import {ConfirmationModal, Button, Dropdown, Icon, Message, LoadingIndicator} from 'components';
import {StoreProvider, withStore} from './OverviewStore';

function Overview({store: {loading, deleting, conflicts}, ...props}) {
  if (loading) {
    return <LoadingIndicator />;
  }

  const error = props.error && (
    <Message type="error">{props.error.errorMessage || props.error.statusText}</Message>
  );

  return (
    <div className="Overview">
      {error}
      <div className="header">
        <div className="createAllButton">
          <Dropdown label="Create New">
            <Dropdown.Option onClick={() => props.setCollectionToUpdate({})}>
              Create Collection
            </Dropdown.Option>
            <Dropdown.Option onClick={props.createDashboard}>Create Dashboard</Dropdown.Option>
            <Dropdown.Submenu label="New Report">
              <Dropdown.Option onClick={props.createProcessReport}>
                Create Process Report
              </Dropdown.Option>
              <Dropdown.Option onClick={props.createCombinedReport}>
                Create Combined Process Report
              </Dropdown.Option>
              <Dropdown.Option onClick={props.createDecisionReport}>
                Create Decision Report
              </Dropdown.Option>
            </Dropdown.Submenu>
          </Dropdown>
        </div>
      </div>
      <Collections />
      <Button color="green" className="createButton" onClick={props.createDashboard}>
        Create New Dashboard
      </Button>
      <Dashboards />
      <div className="createButton">
        <Button color="green" onClick={props.createProcessReport}>
          Create Process Report
        </Button>
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
