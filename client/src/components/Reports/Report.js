/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {withErrorHandling} from 'HOC';
import {ErrorPage, LoadingIndicator, EditCollectionModal} from 'components';
import {loadSingleReport, evaluateReport} from './service';
import {loadEntity, createEntity, getEntitiesCollections} from 'services';

import ReportEdit from './ReportEdit';
import ReportView from './ReportView';

import './Report.scss';

export default withErrorHandling(
  class Report extends React.Component {
    constructor(props) {
      super(props);

      this.id = props.match.params.id;
      this.isNew = props.location.search === '?new';

      this.state = {
        collections: [],
        creatingCollection: false,
        loaded: false,
        serverError: null
      };
    }

    componentDidMount = async () => {
      await this.props.mightFail(
        loadSingleReport(this.id),
        async response => {
          this.setState({
            loaded: true,
            report: (await evaluateReport(this.id)) || response
          });
          await this.loadCollections();
        },
        ({status}) => {
          this.setState({
            serverError: status
          });
          return;
        }
      );
    };

    loadCollections = async () => {
      const collections = await loadEntity('collection', null, 'created');
      this.setState({collections});
    };

    openEditCollectionModal = () => {
      this.setState({creatingCollection: true});
    };

    createCollection = async collection => {
      await createEntity('collection', collection);
      await this.loadCollections();
      this.setState({creatingCollection: false});
    };

    componentDidUpdate() {
      if (this.isNew) {
        this.isNew = false;
      }
    }

    render() {
      const {report, collections, creatingCollection, loaded, serverError} = this.state;

      if (serverError) {
        return <ErrorPage />;
      }

      if (!loaded) {
        return <LoadingIndicator />;
      }

      const {viewMode} = this.props.match.params;

      const commonProps = {
        report,
        collections,
        reportCollections: getEntitiesCollections(collections)[report.id],
        loadCollections: this.loadCollections,
        openEditCollectionModal: this.openEditCollectionModal
      };

      return (
        <div className="Report-container">
          {viewMode === 'edit' ? (
            <ReportEdit
              isNew={this.isNew}
              updateOverview={report => this.setState({report})}
              {...commonProps}
            />
          ) : (
            <ReportView {...commonProps} />
          )}
          {creatingCollection && (
            <EditCollectionModal
              collection={{data: {entities: [report.id]}}}
              onClose={() => this.setState({creatingCollection: false})}
              onConfirm={this.createCollection}
            />
          )}
        </div>
      );
    }
  }
);
