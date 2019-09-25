/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import {Link, withRouter} from 'react-router-dom';
import {matchPath} from 'react-router';
import {loadEntitiesNames} from './service';

import './NavItem.scss';

export default withRouter(
  class NavItem extends React.Component {
    state = {
      breadcrumbs: []
    };

    async componentDidMount() {
      if (this.props.breadcrumbsEntities) {
        await this.constructBreadcrumbs();
      }
    }

    async componentDidUpdate(prevProps) {
      if (
        prevProps.location.pathname !== this.props.location.pathname &&
        this.props.breadcrumbsEntities
      ) {
        await this.constructBreadcrumbs();
      }
    }

    constructBreadcrumbs = async () => {
      const {
        location: {pathname},
        breadcrumbsEntities
      } = this.props;

      let breadcrumbs = [];
      const entitiesIds = {};
      for (let entity of breadcrumbsEntities) {
        const splittedUrl = pathname.split(`/${entity}/`);
        if (splittedUrl[1]) {
          const id = splittedUrl[1].split('/')[0];
          if (!id.includes('new')) {
            entitiesIds[entity + 'Id'] = id;
            breadcrumbs.push({
              id,
              type: entity,
              url: splittedUrl[0] + `/${entity}/${id}/`
            });
          }
        }
      }

      if (breadcrumbs.length > 0) {
        const names = await loadEntitiesNames(entitiesIds);
        breadcrumbs = breadcrumbs.map(breadcrumb => ({
          ...breadcrumb,
          name: names[breadcrumb.type + 'Name']
        }));
      }

      this.setState({breadcrumbs});
    };

    isActive = () =>
      matchPath(this.props.location.pathname, {path: this.props.active, exact: true}) !== null;

    render() {
      const {name, activeBorder, linksTo} = this.props;

      return (
        <li className={classnames('NavItem', {active: this.isActive(), activeBorder})}>
          <Link to={linksTo} title={name} replace={this.isActive()}>
            {name}
          </Link>
          {this.isActive() &&
            this.state.breadcrumbs.map(({id, name, url}) => (
              <Link title={name} className="breadcrumb" key={id} to={url}>
                <span>›</span>
                {name}
              </Link>
            ))}
        </li>
      );
    }
  }
);
