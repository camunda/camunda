import React from 'react';
import moment from 'moment';
import classnames from 'classnames';

import {withErrorHandling} from 'HOC';
import {Redirect, Link} from 'react-router-dom';

import {Button, Modal, Message, Icon, Input, LoadingIndicator} from 'components';

import {load, create, remove, duplicate, update} from './service';

import {formatters} from 'services';

import entityIcons from './entityIcons';

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
    if (this.props.EditModal) return this.openNewEditModal();
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
    const newName = this.getNewReportName(this.state.data, name);
    const copy = {...(data && {data}), ...(reports && {reports}), name: newName};
    await duplicate(this.props.api, copy);
    // fetch the data again after duplication to update the state
    await this.loadData();
  };

  getNewReportName = (reports, name) => {
    // remove any additions to the name
    const originalName = name.split(' - Copy')[0];
    const copyName = `${originalName} - Copy`;
    // get the duplicate numbers
    const numbers = reports
      .filter(report => report.name.includes(copyName))
      .map(this.getDuplicatesNumbers);
    // if the name is the original name and no duplicates then return the copy name
    if (!name.includes('Copy') && !numbers.length) return copyName;
    // otherwise append also the next number
    return `${copyName} ${Math.max(...numbers) + 1}`;
  };

  getDuplicatesNumbers = copy => {
    const pieces = copy.name.split(' ');
    const reportNumber = Number(pieces[pieces.length - 1]);
    return isNaN(reportNumber) ? 1 : reportNumber;
  };

  closeDeleteModal = () => {
    this.setState({
      deleteModalVisible: false,
      deleteModalEntity: {}
    });
  };

  getEntityIconName = entity => {
    const entityType = this.props.api;
    if (!entity.data || !entity.data.visualization) return entityType;
    const visualization = entity.data.visualization;
    return entityType + visualization.charAt(0).toUpperCase() + visualization.slice(1);
  };

  formatData = data =>
    data.map(entity => {
      const {name, id, lastModified, lastModifier, shared} = entity;
      const entry = {
        name,
        link: `/${this.props.api}/${id}`,
        iconName: this.getEntityIconName(entity),
        infos: [
          {
            parentClassName: 'custom',
            content: this.props.renderCustom && this.props.renderCustom(entity)
          },
          {
            parentClassName: 'dataMeta',
            content: (
              <React.Fragment>
                {`Last modified ${moment(lastModified).format('lll')} by `}
                <strong>{lastModifier}</strong>
              </React.Fragment>
            )
          },
          {
            parentClassName: 'dataIcons',
            content: shared && <Icon type="share" title={`This ${this.props.label} is shared`} />
          }
        ],
        operations: [],
        editData: entity
      };

      if (this.props.operations.includes('edit')) {
        entry.operations.push({
          content: <Icon type="edit" title={`Edit ${this.props.api}`} className="editLink" />,
          link: `/${this.props.api}/${id}/edit`,
          editData: entity,
          parentClassName: 'dataTool'
        });
      }

      if (this.props.operations.includes('duplicate')) {
        entry.operations.push({
          content: (
            <Icon
              type="copy-document"
              title={`Duplicate ${this.props.api}`}
              onClick={this.duplicateEntity(id)}
              className="duplicateIcon"
            />
          ),
          parentClassName: 'dataTool'
        });
      }

      if (this.props.operations.includes('delete')) {
        entry.operations.push({
          content: (
            <Icon
              type="delete"
              title={`Delete ${this.props.api}`}
              onClick={this.showDeleteModal({id, name})}
              className="deleteIcon"
            />
          ),
          parentClassName: 'dataTool'
        });
      }

      return entry;
    });

  openNewEditModal = () => {
    this.setState({
      editEntity: {}
    });
  };

  closeEditModal = () => {
    this.setState({
      editEntity: null
    });
  };

  confirmEditModal = async entity => {
    const editEntity = this.state.editEntity;
    if (editEntity.id) {
      await update(this.props.api, editEntity.id, entity);
    } else {
      await create(this.props.api, entity);
    }
    this.closeEditModal();
    await this.loadData();
  };

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

  renderCells = data => {
    return data.map(
      (cell, idx) =>
        cell.content && (
          <span key={idx} className={classnames('data', cell.parentClassName)}>
            {this.renderCell(cell)}
          </span>
        )
    );
  };

  renderCell = cell => {
    if (cell.link) {
      return this.renderLink(cell);
    }
    return cell.content;
  };

  // if a modal is provided add onClick event otherwise use a router Link
  renderLink = data => {
    const {EditModal} = this.props;
    const EntityLink = EditModal ? 'a' : Link;
    const linkProps = {
      to: EditModal ? undefined : data.link,
      onClick: EditModal ? () => this.setState({editEntity: data.editData}) : undefined,
      className: data.className
    };
    return (
      <EntityLink {...linkProps}>
        {data.title ? (
          <React.Fragment>
            <span className="data visualizationIcon">
              <data.Icon />
            </span>
            <div className="textInfo">
              <span className="data dataTitle">
                {formatters.getHighlightedText(data.title, this.state.query)}
              </span>
              <div className="extraInfo">{data.content}</div>
            </div>
          </React.Fragment>
        ) : (
          data.content
        )}
      </EntityLink>
    );
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
    const HeaderIcon = entityIcons[this.props.api + 's'];
    const header = (
      <div className="header">
        {HeaderIcon && <HeaderIcon />}
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
    const {EditModal} = this.props;
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
            {`There are no ${this.props.label}s configured.`}
            {createLink}
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
                    {this.renderLink({
                      title: row.name,
                      Icon: entityIcons[row.iconName],
                      content: this.renderCells(row.infos),
                      link: row.link,
                      className: 'info',
                      editData: row.editData
                    })}
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
          {EditModal && (
            <EditModal
              onConfirm={this.confirmEditModal}
              onClose={this.closeEditModal}
              alert={this.state.editEntity}
            />
          )}
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
