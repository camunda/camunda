/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';
import {Link, withRouter} from 'react-router-dom';
import {matchPath} from 'react-router';

import {Tooltip} from 'components';

import {loadEntitiesNames} from './service';

import './NavItem.scss';

const breadcrumbConstructors = [];

export default withRouter(
  class NavItem extends React.Component {
    state = {
      breadcrumbs: [],
    };

    async componentDidMount() {
      await this.constructBreadcrumbs();
      breadcrumbConstructors.push(this.constructBreadcrumbs);
    }

    componentWillUnmount() {
      breadcrumbConstructors.splice(breadcrumbConstructors.indexOf(this.constructBreadcrumbs), 1);
    }

    async componentDidUpdate(prevProps) {
      if (prevProps.location.pathname !== this.props.location.pathname) {
        await this.constructBreadcrumbs();
      }
    }

    constructBreadcrumbs = async () => {
      const {
        location: {pathname},
        breadcrumbsEntities,
      } = this.props;

      if (!breadcrumbsEntities) {
        return;
      }

      let breadcrumbs = [];
      const entitiesIds = {};
      breadcrumbsEntities.forEach((entity) => {
        const entityUrl = entity === 'eventBasedProcess' ? 'events/processes' : entity;
        const splittedUrl = pathname.split(`/${entityUrl}/`);
        if (splittedUrl[1]) {
          const id = splittedUrl[1].split('/')[0];
          if (!id.includes('new')) {
            entitiesIds[entity + 'Id'] = id;
            breadcrumbs.push({
              id,
              type: entity,
              url: splittedUrl[0] + `/${entityUrl}/${id}/`,
            });
          }
        }
      });

      if (breadcrumbs.length > 0) {
        const names = await loadEntitiesNames(entitiesIds);
        breadcrumbs = breadcrumbs.map((breadcrumb) => ({
          ...breadcrumb,
          name: names[breadcrumb.type + 'Name'],
        }));
      }

      this.setState({breadcrumbs});
    };

    isActive = () =>
      matchPath(this.props.location.pathname, {path: this.props.active, exact: true}) !== null;

    render() {
      const {name, activeBorder, linksTo} = this.props;
      const {breadcrumbs} = this.state;
      const breadcrumbsCount = breadcrumbs.length;

      const active = this.isActive();

      return (
        <li className={classnames('NavItem', {activeBorder, active})}>
          <Tooltip content={name} position="bottom" overflowOnly>
            <Link
              to={linksTo}
              className={classnames({active: !breadcrumbsCount && active})}
              replace={active}
            >
              {name}
            </Link>
          </Tooltip>
          {active &&
            breadcrumbs.map(({id, name, url}, i) => (
              <Tooltip content={name} key={id} position="bottom" overflowOnly>
                <Link className="breadcrumb" key={id} to={url}>
                  <span className="arrow">›</span>
                  <span className={classnames({active: breadcrumbsCount - 1 === i})}>{name}</span>
                </Link>
              </Tooltip>
            ))}
        </li>
      );
    }
  }
);

export function refreshBreadcrumbs() {
  breadcrumbConstructors.forEach((fct) => fct());
}
