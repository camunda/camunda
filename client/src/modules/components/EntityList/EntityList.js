import React from 'react';
import moment from 'moment';
import classnames from 'classnames';

import {withErrorHandling} from 'HOC';
import {Redirect, Link} from 'react-router-dom';

import {Button, Modal, Message, Icon, Input, LoadingIndicator} from 'components';

import {load, create, remove, duplicate} from './service';

import {formatters} from 'services';

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
      query: ''
    };
  }

  componentDidMount = async () => {
    await this.loadData();
  };

  loadData = async () => {
    this.props.mightFail(
      load(this.props.api, this.props.displayOnly, this.props.sortBy),
      response => {
        this.setState({
          data: response,
          loaded: true
        });
      }
    );
  };

  createEntity = async evt => {
    this.setState({
      redirectToEntity: await create(this.props.api)
    });
  };

  deleteEntity = id => evt => {
    remove(id, this.props.api);

    this.setState({
      data: this.state.data.filter(entity => entity.id !== id)
    });
    this.closeDeleteModal();
  };

  showDeleteModal = ({id, name}) => evt => {
    this.setState({
      deleteModalVisible: true,
      deleteModalEntity: {id, name}
    });
  };

  duplicateEntity = id => async evt => {
    const {data, reports, name} = this.state.data.find(entity => entity.id === id);
    const copy = {...(data && {data}), ...(reports && {reports}), name: `Copy of "${name}"`};
    await duplicate(this.props.api, copy);
    // fetch the data again after duplication to update the state
    await this.loadData();
  };

  closeDeleteModal = () => {
    this.setState({
      deleteModalVisible: false,
      deleteModalEntity: {}
    });
  };

  formatData = data =>
    data.map(({name, id, lastModified, lastModifier, shared}) => {
      const entry = {
        name,
        link: `/${this.props.api}/${id}`,
        infos: [
          {
            content: name,
            parentClassName: 'dataTitle'
          },
          {
            content: `Last modified ${moment(lastModified).format('lll')} by ${lastModifier}`,
            parentClassName: 'dataMeta'
          },
          {
            parentClassName: 'dataIcons',
            content: shared && <Icon type="share" title={`This ${this.props.label} is shared`} />
          }
        ],
        operations: []
      };

      if (this.props.operations.includes('delete')) {
        entry.operations.push({
          content: (
            <Icon
              type="delete"
              title="Delete a report"
              onClick={this.showDeleteModal({id, name})}
              className="deleteIcon"
            />
          ),
          parentClassName: 'dataTool'
        });
      }

      if (this.props.operations.includes('duplicate')) {
        entry.operations.push({
          content: (
            <Icon
              type="copy-document"
              title="Duplicate a report"
              onClick={this.duplicateEntity(id)}
              className="duplicateIcon"
            />
          ),
          parentClassName: 'dataTool'
        });
      }

      if (this.props.operations.includes('edit')) {
        entry.operations.push({
          content: <Icon type="edit" title="Edit a report" className="editLink" />,
          link: `/${this.props.api}/${id}/edit`,
          parentClassName: 'dataTool'
        });
      }

      return entry;
    });

  renderModal = () => {
    const {deleteModalVisible, deleteModalEntity} = this.state;
    return (
      <Modal
        open={deleteModalVisible}
        onClose={this.closeDeleteModal}
        onConfirm={this.deleteEntity(deleteModalEntity.id)}
        className="deleteModal"
      >
        <Modal.Header>Delete {deleteModalEntity.name}</Modal.Header>
        <Modal.Content>
          <p>You are about to delete {deleteModalEntity.name}. Are you sure you want to proceed?</p>
        </Modal.Content>
        <Modal.Actions>
          <Button className="deleteModalButton" onClick={this.closeDeleteModal}>
            Cancel
          </Button>
          <Button
            type="primary"
            color="red"
            className="deleteEntityModalButton"
            onClick={this.deleteEntity(deleteModalEntity.id)}
          >
            Delete
          </Button>
        </Modal.Actions>
      </Modal>
    );
  };

  renderCell = cell => {
    if (cell.link) {
      return (
        <Link to={cell.link} className={cell.className}>
          {cell.content}
        </Link>
      );
    }
    if (cell.parentClassName === 'dataTitle')
      return formatters.getHighlightedText(cell.content, this.state.query);
    return cell.content;
  };

  renderCells = data => {
    return data.map((cell, idx) => (
      <span key={idx} className={classnames('data', cell.parentClassName)}>
        {this.renderCell(cell)}
      </span>
    ));
  };

  render() {
    let createButton = null;
    let searchInput = null;
    if (this.props.operations.includes('create')) {
      createButton = (
        <Button color="green" className="createButton" onClick={this.createEntity}>
          Create new {this.props.label}
        </Button>
      );
    }
    if (this.props.operations.includes('search')) {
      searchInput = (
        <Input
          className="input"
          onChange={({target: {value}}) => this.setState({query: value})}
          placeholder="Filter for name"
        />
      );
    }

    const header = (
      <div className="header">
        <h1 className="heading">{this.props.label}s</h1>
        <div className="tools">{createButton}</div>
      </div>
    );

    if (this.props.error) {
      const {error} = this.props;
      let errorMessage = 'Data could not be loaded. ';
      errorMessage += error.errorMessage || error.statusText || '';

      return (
        <section className="EntityList">
          {header}
          <Message type="error">{errorMessage}</Message>
        </section>
      );
    }

    const {redirectToEntity, loaded} = this.state;
    const {includeViewAllLink} = this.props;
    const modal = this.renderModal();
    const isListEmpty = this.state.data.length === 0;

    const createLink = (
      <a className="createLink" role="button" onClick={this.createEntity}>
        Create a new {this.props.label}…
      </a>
    );

    let list;
    if (loaded) {
      list = isListEmpty ? (
        <ul className="list">
          <li className="item noEntities">
            {`You have no ${this.props.label}s configured yet.`}&nbsp;{createLink}
          </li>
        </ul>
      ) : (
        <React.Fragment>
          {searchInput}
          <ul className="list">
            {this.formatData(this.state.data)
              .filter(row => row.name.toLowerCase().includes(this.state.query.toLowerCase()))
              .map((row, idx) => {
                return (
                  <li key={idx} className="item">
                    <Link to={row.link} className="info">
                      {this.renderCells(row.infos)}
                    </Link>
                    <div className="operations">{this.renderCells(row.operations)}</div>
                  </li>
                );
              })}
          </ul>
        </React.Fragment>
      );
    } else {
      list = <LoadingIndicator />;
    }

    if (redirectToEntity !== false) {
      return <Redirect to={`/${this.props.api}/${redirectToEntity}/edit?new`} />;
    } else {
      return (
        <section className="EntityList">
          {header}
          {list}
          {modal}
          {this.props.children}
          {includeViewAllLink && !isListEmpty ? (
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
}

export default withErrorHandling(EntityList);

EntityList.defaultProps = {
  operations: ['create', 'edit', 'delete']
};
