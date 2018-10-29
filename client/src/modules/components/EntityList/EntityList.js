import React from 'react';

import {withErrorHandling} from 'HOC';
import {Redirect, Link} from 'react-router-dom';

import {Button, Message, Input, LoadingIndicator, ConfirmationModal} from 'components';

import {load, create, remove, duplicate, update} from './service';
import {checkDeleteConflict} from 'services';

import entityIcons from './entityIcons';

import EntityItem from './EntityItem';

import './EntityList.scss';

class EntityList extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: [],
      redirectToEntity: false,
      loaded: false,
      confirmModalVisible: false,
      deleteModalEntity: {},
      query: '',
      editEntity: null
    };
  }

  componentDidMount = async () => {
    await this.loadData();
  };

  loadData = async () => {
    this.props.mightFail(
      await load(this.props.api, this.props.loadOnly, this.props.sortBy),
      response => {
        this.setState({
          data: response,
          loaded: true
        });
      }
    );
  };

  createEntity = type => async evt => {
    if (this.props.ContentPanel) return this.openNewContentPanel();
    this.setState({
      redirectToEntity: await create(this.props.api, {reportType: type})
    });
  };

  deleteEntity = evt => {
    const id = this.state.deleteModalEntity.id;
    remove(id, this.props.api);

    this.setState({
      data: this.filterEntity(id)
    });
    this.closeConfirmModal();
  };

  filterEntity = id => {
    return this.state.data.filter(entity => entity.id !== id).map(entity => {
      if (
        entity.reportType &&
        entity.reportType === 'combined' &&
        entity.data &&
        entity.data.reportIds &&
        entity.data.reportIds.includes(id)
      ) {
        const newReportIds = entity.data.reportIds.filter(item => item !== id);
        return {...entity, data: {...entity.data, reportIds: newReportIds}};
      }
      return entity;
    });
  };

  duplicateEntity = async id => {
    const {data, reports, name, reportType} = this.state.data.find(entity => entity.id === id);
    const copy = {
      ...(data && {data}),
      ...(reports && {reports}),
      name: `${name} - Copy`,
      reportType
    };
    await duplicate(this.props.api, copy);
    // fetch the data again after duplication to update the state
    await this.loadData();
  };

  openNewContentPanel = () => {
    this.setState({
      editEntity: {}
    });
  };

  closeContentPanel = () => {
    this.setState({
      editEntity: null
    });
  };

  confirmContentPanel = async entity => {
    const editEntity = this.state.editEntity;
    if (editEntity.id) {
      await update(this.props.api, editEntity.id, entity);
    } else {
      await create(this.props.api, entity);
    }
    this.closeContentPanel();
    await this.loadData();
  };

  showDeleteModal = async ({id, name}) => {
    const response = await checkDeleteConflict(id, this.props.api);
    const newState = {
      confirmModalVisible: true,
      deleteModalEntity: {id, name},
      conflict: null
    };

    if (response && response.conflictedItems && response.conflictedItems.length) {
      newState.conflict = {
        type: 'Delete',
        items: response.conflictedItems
      };
    }

    this.setState(newState);
  };

  closeConfirmModal = () => {
    this.setState({
      confirmModalVisible: false,
      deleteModalEntity: {}
    });
  };

  renderHeader = () => {
    const {operations, label, api} = this.props;
    const HeaderIcon = entityIcons[api].header.Component;
    return (
      <div className="header">
        {HeaderIcon && <HeaderIcon />}
        <h1 className="heading">{label}s</h1>
        <div className="tools">
          {operations.includes('combine') && (
            <Button color="green" className="combineButton" onClick={this.createEntity('combined')}>
              Create a Combined {label}
            </Button>
          )}
          {operations.includes('create') && (
            <Button color="green" className="createButton" onClick={this.createEntity('single')}>
              Create New {label}
            </Button>
          )}
        </div>
      </div>
    );
  };

  renderErrorMessage = (error, header) => {
    let errorMessage = 'Data could not be loaded. ';
    errorMessage += error.errorMessage || error.statusText || '';

    return (
      <section className="EntityList">
        {header}
        <Message type="error">{errorMessage}</Message>
      </section>
    );
  };

  renderList = () => {
    let {data} = this.state;
    const {operations, label, api, renderCustom, ContentPanel, displayOnly} = this.props;

    if (displayOnly && data.length > displayOnly) {
      data = data.slice(0, displayOnly);
    }

    const list =
      data.length === 0 ? (
        <ul className="list">
          <li className="item noEntities">
            {`There are no ${this.props.label}s configured.`}
            <a className="createLink" role="button" onClick={this.createEntity('single')}>
              Create a new {this.props.label}…
            </a>
          </li>
        </ul>
      ) : (
        <React.Fragment>
          {this.props.operations.includes('search') && (
            <Input
              className="input"
              onChange={({target: {value}}) => this.setState({query: value})}
              placeholder="Filter for name"
            />
          )}
          <ul className="list">
            {data
              .filter(entity => entity.name.toLowerCase().includes(this.state.query.toLowerCase()))
              .map((itemData, idx) => (
                <EntityItem
                  key={idx}
                  operations={operations}
                  label={label}
                  api={api}
                  renderCustom={renderCustom}
                  data={itemData}
                  ContentPanel={ContentPanel}
                  updateEntity={this.updateEntity}
                  duplicateEntity={this.duplicateEntity}
                  showDeleteModal={this.showDeleteModal}
                  query={this.state.query}
                  getReportVis={this.getReportVis}
                />
              ))}
          </ul>
        </React.Fragment>
      );
    return list;
  };

  getReportVis = reportId => {
    const report = this.state.data.find(report => report.id === reportId);
    if (!report) return null;
    return report.data.visualization;
  };

  updateEntity = editEntity => {
    this.setState({
      editEntity
    });
  };

  render() {
    const {error, includeViewAllLink, ContentPanel} = this.props;
    const {redirectToEntity, confirmModalVisible, deleteModalEntity, loaded} = this.state;

    if (redirectToEntity !== false) {
      return <Redirect to={`/${this.props.api}/${redirectToEntity}/edit?new`} />;
    }

    const header = this.renderHeader();

    if (error) return this.renderErrorMessage(error, header);

    const list = loaded ? this.renderList() : <LoadingIndicator />;
    return (
      <section className="EntityList">
        {header}
        {list}
        <ConfirmationModal
          open={confirmModalVisible}
          onClose={this.closeConfirmModal}
          onConfirm={this.deleteEntity}
          entityName={deleteModalEntity.name}
          conflict={this.state.conflict}
        />
        {this.state.editEntity && (
          <ContentPanel
            onConfirm={this.confirmContentPanel}
            onClose={this.closeContentPanel}
            entity={this.state.editEntity}
          />
        )}
        {this.props.children}
        {includeViewAllLink && this.state.data.length ? (
          <Link to={`/${this.props.api}s`} className="small">
            View all {`${this.props.label}`}s…
          </Link>
        ) : (
          ''
        )}
      </section>
    );
  }
}

export default withErrorHandling(EntityList);

EntityList.defaultProps = {
  operations: ['create', 'edit', 'delete']
};
