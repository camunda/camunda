import React from 'react';
import moment from 'moment';
import {Redirect} from 'react-router-dom';

import {Table, Button} from 'components';

import {load, create, remove} from './service';

export default class EntityList extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: [],
      redirectToEntity: false,
      loaded: false
    };

    this.loadEntities();
  }

  loadEntities = async () => {
    const response = await load(this.props.api, this.props.displayOnly, this.props.sortBy);
    this.setState({
      data: response,
      loaded: true
    });
  }

  createEntity = async evt => {
    this.setState({
      redirectToEntity: await create(this.props.api)
    });
  }

  deleteEntity = id => evt => {
    remove(id, this.props.api);

    this.setState({
      data: this.state.data.filter(entity => entity.id !== id)
    });
  };

  formatData = data => data.map(({name, id, lastModified, lastModifier}) => {
    const entry = [
      {content: name, link: `/${this.props.api}/${id}`},
      `Last modified at ${moment(lastModified).format('lll')}`,
      `by ${lastModifier}`
    ];

    if(this.props.operations.includes('delete')) {
      entry.push({
        content: 'Delete',
        onClick: this.deleteEntity(id),
        className: 'Button Button--small EntityList__deleteButton'
      });
    }
    if(this.props.operations.includes('edit')) {
      entry.push({
        content: 'Edit',
        link: `/${this.props.api}/${id}/edit`,
        className: 'EntityList__editLink'
      });
    }

    return entry;
  })

  render() {
    const {redirectToEntity, loaded} = this.state;

    let createButton = null;
    if(this.props.operations.includes('create')) {
      createButton = <Button className='EntityList__createButton' onClick={this.createEntity}>Create New {this.props.label}</Button>;
    }

    const header = <h1>{this.props.label}s</h1>;

    let list;
    if(loaded) {
      list = (<ul>
        {this.formatData(this.state.data).map((row, idx) => {
          return (<li key={idx}>
            {row.map((cell, idx) => {
              return (<span key={idx}>
                {Table.renderCell(cell)}
              </span>);
            })}
          </li>);
        })}
      </ul>);
    } else {
      list = <div>loading...</div>;
    }

    if(redirectToEntity !== false) {
      return (<Redirect to={`/${this.props.api}/${redirectToEntity}/edit`} />);
    } else {
      return (<section>
        {createButton}
        {header}
        {list}
        {this.props.children}
      </section>);
    }
  }
}

EntityList.defaultProps = {
  operations: ['create', 'edit', 'delete']
};
