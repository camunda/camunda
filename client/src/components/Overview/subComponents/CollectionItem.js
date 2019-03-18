import React from 'react';
import {Button, Icon} from 'components';
import classnames from 'classnames';

import entityIcons from '../entityIcons';
import LastModified from './LastModified';

const EntityIcon = entityIcons.collection.generic.Component;
const OpenCloseIcon = entityIcons.entityOpenClose;

export default class CollectionItem extends React.Component {
  state = {
    exanded: false
  };

  toggleExpanded = evt => {
    this.setState({expanded: !this.state.expanded});
  };

  render() {
    const {
      collection: {
        id,
        name,
        created,
        owner,
        data: {entities}
      },
      setCollectionToUpdate,
      showDeleteModalFor,
      children
    } = this.props;

    return (
      <li className="CollectionItem">
        <div className="listItem">
          <Button className="info ToggleCollapse" onClick={this.toggleExpanded}>
            <OpenCloseIcon className={classnames('collapseIcon', {right: !this.state.expanded})} />
            <span className="icon">
              <EntityIcon />
            </span>
            <div className="textInfo">
              <div className="dataTitle">
                <h2>{name}</h2>
              </div>
              <div className="extraInfo">
                <div className="custom">
                  <span>{entities.length} Items</span>
                </div>
                <LastModified label="Created" date={created} author={owner} />
              </div>
            </div>
          </Button>
          <div className="entityCollections" />
          <div className="operations">
            <Button
              title="Edit Collection"
              onClick={() =>
                setCollectionToUpdate({id, name, data: {entities: entities.map(({id}) => id)}})
              }
            >
              <Icon title="Edit Collection" type="edit" className="editLink" />
            </Button>
            <Button
              title="Delete Report"
              onClick={showDeleteModalFor({type: 'collection', entity: {id, name}})}
            >
              <Icon type="delete" title="Delete Report" className="deleteIcon" />
            </Button>
          </div>
        </div>
        {this.state.expanded && children}
      </li>
    );
  }
}
