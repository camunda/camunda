import React from 'react';

import {withErrorHandling} from 'HOC';
import {Redirect, Link} from 'react-router-dom';

import {Button, Message, Input, LoadingIndicator} from 'components';

import {load, create, remove, duplicate, update} from './service';

import entityIcons from './entityIcons';

import DeleteModal from './DeleteModal';
import EntityItem from './EntityItem';

import './EntityList.css';

class EntityList extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: [],
      redirectToEntity: false,
      loaded: false,
      deleteModalVisible: false,
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
      await load(this.props.api, this.props.displayOnly, this.props.sortBy),
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
      data: this.state.data.filter(entity => entity.id !== id)
    });
    this.closeDeleteModal();
  };

  duplicateEntity = id => async evt => {
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

  getEntityIconName = entity => {
    if (entity.data && entity.data.visualization) return entity.data.visualization;
    return 'generic';
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

  showDeleteModal = ({id, name}) => evt => {
    this.setState({
      deleteModalVisible: true,
      deleteModalEntity: {id, name}
    });
  };

  closeDeleteModal = () => {
    this.setState({
      deleteModalVisible: false,
      deleteModalEntity: {}
    });
  };

  renderHeader = () => {
    const {operations, label, api} = this.props;
    const HeaderIcon = entityIcons[api].header;
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
    const {data} = this.state;
    const {operations, label, api, renderCustom, ContentPanel} = this.props;

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
                />
              ))}
          </ul>
        </React.Fragment>
      );
    return list;
  };

  updateEntity = editEntity => {
    this.setState({
      editEntity
    });
  };

  render() {
    const {error, includeViewAllLink, ContentPanel} = this.props;
    const {redirectToEntity, deleteModalVisible, deleteModalEntity, loaded} = this.state;

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
        <DeleteModal
          isVisible={deleteModalVisible}
          entityName={deleteModalEntity.name}
          deleteEntity={this.deleteEntity}
          closeModal={this.closeDeleteModal}
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
