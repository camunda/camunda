import React from 'react';
import moment from 'moment';
import classnames from 'classnames';

import {withErrorHandling} from 'HOC';
import {Redirect, Link} from 'react-router-dom';

import {Button, Modal, Message, Icon} from 'components';

import {load, create, remove} from './service';

import './EntityList.css';

class EntityList extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: [],
      redirectToEntity: false,
      loaded: false,
      deleteModalVisible: false,
      deleteModalEntity: {}
    };
  }

  componentDidMount = async () => {
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

  closeDeleteModal = () => {
    this.setState({
      deleteModalVisible: false,
      deleteModalEntity: {}
    });
  };

  formatData = data =>
    data.map(({name, id, lastModified, lastModifier, shared}) => {
      const entry = [
        {
          content: name,
          link: `/${this.props.api}/${id}`,
          parentClassName: 'EntityList__data--title'
        },
        {
          plainText: true,
          content: `Last modified ${moment(lastModified).format('lll')} by ${lastModifier}`,
          parentClassName: 'EntityList__data--metadata'
        },
        {
          shared,
          parentClassName: 'EntityList__data--icons'
        }
      ];

      if (this.props.operations.includes('delete')) {
        entry.push({
          content: 'delete',
          onClick: this.showDeleteModal({id, name}),
          props: {type: 'small'},
          className: 'EntityList__deleteIcon',
          parentClassName: 'EntityList__data--tool'
        });
      }
      if (this.props.operations.includes('edit')) {
        entry.push({
          content: <Icon type="edit" className="EntityList__editLink" />,
          link: `/${this.props.api}/${id}/edit`,
          parentClassName: 'EntityList__data--tool'
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
        className="EntityList__delete-modal"
      >
        <Modal.Header>Delete {deleteModalEntity.name}</Modal.Header>
        <Modal.Content>
          <p>You are about to delete {deleteModalEntity.name}. Are you sure you want to proceed?</p>
        </Modal.Content>
        <Modal.Actions>
          <Button className="EntityList__close-delete-modal-button" onClick={this.closeDeleteModal}>
            Cancel
          </Button>
          <Button
            type="primary"
            color="red"
            className="EntityList__delete-entity-modal-button"
            onClick={this.deleteEntity(deleteModalEntity.id)}
          >
            Delete
          </Button>
        </Modal.Actions>
      </Modal>
    );
  };

  renderCell = cell => {
    if (cell.plainText) {
      return cell.content;
    }

    if (cell.shared) {
      return <Icon type="share" title={`This ${this.props.label} is shared`} />;
    }

    if (cell.link) {
      return (
        <Link to={cell.link} className={cell.className}>
          {cell.content}
        </Link>
      );
    }

    if (cell.onClick) {
      return (
        <Icon
          {...cell.props}
          onClick={cell.onClick}
          className={cell.className}
          type={cell.content}
        />
      );
    }
  };

  render() {
    let createButton = null;
    if (this.props.operations.includes('create')) {
      createButton = (
        <Button color="green" className="EntityList__createButton" onClick={this.createEntity}>
          Create new {this.props.label}
        </Button>
      );
    }

    const header = (
      <div className="EntityList__header">
        <h1 className="EntityList__heading">{this.props.label}s</h1>
        <div className="EntityList__tools">{createButton}</div>
      </div>
    );

    if (this.props.error) {
      const {error} = this.props;
      let errorMessage = 'Data could not be loaded. ';
      errorMessage += error.errorMessage || error.statusText || '';

      return (
        <section className="EntityList">
          {header}
          <Message type="error" message={errorMessage} />
        </section>
      );
    }

    const {redirectToEntity, loaded} = this.state;
    const {includeViewAllLink} = this.props;
    const modal = this.renderModal();
    const isListEmpty = this.state.data.length === 0;

    const createLink = (
      <a className="EntityList__createLink" role="button" onClick={this.createEntity}>
        Create a new {this.props.label}…
      </a>
    );

    let list;
    if (loaded) {
      list = isListEmpty ? (
        <ul className="EntityList__list">
          <li className="EntityList__item EntityList__no-entities">
            {`You have no ${this.props.label}s configured yet.`}&nbsp;{createLink}
          </li>
        </ul>
      ) : (
        <ul className="EntityList__list">
          {this.formatData(this.state.data).map((row, idx) => {
            return (
              <li key={idx} className="EntityList__item">
                {row.map((cell, idx) => {
                  return (
                    <span
                      key={idx}
                      className={classnames('EntityList__data', cell.parentClassName)}
                    >
                      {this.renderCell(cell)}
                    </span>
                  );
                })}
              </li>
            );
          })}
        </ul>
      );
    } else {
      list = <div>loading...</div>;
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
