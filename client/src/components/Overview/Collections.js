import React from 'react';
import {Button} from 'components';

import UpdateCollectionModal from './subComponents/UpdateCollectionModal';
import CollectionItem from './subComponents/CollectionItem';
import ReportItem from './subComponents/ReportItem';

import './Collections.scss';

class Collections extends React.Component {
  state = {
    showAllId: null
  };

  render() {
    const empty = this.props.collections.length === 0 && (
      <div className="collectionBlankSlate">
        <strong>Group Reports and Dashboards into Collections.</strong> <br />
        <Button
          type="link"
          className="createLink"
          onClick={() => this.props.setCollectionToUpdate({})}
        >
          Create a Collection…
        </Button>
      </div>
    );

    return (
      <div className="Collections">
        <ul className="entityList">
          {empty}
          {this.props.collections.length > 0 &&
            this.props.collections.map(collection => (
              <CollectionItem
                key={collection.id}
                collection={collection}
                setCollectionToUpdate={this.props.setCollectionToUpdate}
                showDeleteModalFor={this.props.showDeleteModalFor}
              >
                {collection.data.entities.length > 0 ? (
                  <ul className="entityList">
                    {collection.data.entities
                      .slice(0, this.state.showAllId === collection.id ? undefined : 5)
                      .map(entity => (
                        <ReportItem
                          key={entity.id}
                          report={entity}
                          collection={collection}
                          showDeleteModalFor={this.props.showDeleteModalFor}
                          duplicateEntity={this.props.duplicateEntity}
                          renderCollectionsDropdown={this.props.renderCollectionsDropdown}
                        />
                      ))}
                  </ul>
                ) : (
                  <p className="emptyCollection">There are no items in this Collection.</p>
                )}
                <div className="showAll">
                  {!this.state.loading &&
                    collection.data.entities.length > 5 &&
                    (this.state.showAllId !== collection.id ? (
                      <>
                        {collection.data.entities.length} Reports.{' '}
                        <Button
                          type="link"
                          onClick={() => this.setState({showAllId: collection.id})}
                        >
                          Show all...
                        </Button>
                      </>
                    ) : (
                      <Button type="link" onClick={() => this.setState({showAllId: null})}>
                        Show less...
                      </Button>
                    ))}
                </div>
              </CollectionItem>
            ))}
        </ul>
        {this.props.updating && (
          <UpdateCollectionModal
            collection={this.props.updating}
            onClose={() => this.props.setCollectionToUpdate(null)}
            onConfirm={this.props.updateOrCreateCollection}
          />
        )}
      </div>
    );
  }
}

export default Collections;
