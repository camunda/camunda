/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Link, withRouter} from 'react-router-dom';

import {Logo} from 'components';
import HeaderNav from './HeaderNav';
import LogoutButton from './LogoutButton';
import {t} from 'translation';
import {getUiConfig} from './service';
import classnames from 'classnames';
import {withErrorHandling} from 'HOC';
import {addNotification} from 'notifications';

import './Header.scss';

class Header extends React.Component {
  state = {
    config: {header: {}}
  };

  async componentDidMount() {
    this.props.mightFail(
      getUiConfig(),
      config => this.setState({config}),
      () => addNotification({type: 'error', text: t('navigation.configLoadingError')})
    );
  }

  render() {
    const {name, location} = this.props;
    const {
      config: {header}
    } = this.state;

    return (
      <header
        style={{backgroundColor: header.backgroundColor}}
        role="banner"
        className={classnames('Header', {['text-' + header.textColor]: header.textColor})}
      >
        <Link to="/" replace={location.pathname === '/'} className="Header__link" title={name}>
          <Logo src={header.logo} className="Header__logo" />
          <span>{name}</span>
        </Link>
        <HeaderNav>
          <HeaderNav.Item
            name={t('navigation.homepage')}
            linksTo="/"
            active={['/', '/report/*', '/dashboard/*', '/collection/*']}
            breadcrumbsEntities={['collection', 'dashboard', 'report']}
          />
          <HeaderNav.Item
            name={t('navigation.analysis')}
            linksTo="/analysis"
            active={['/analysis/', '/analysis/*']}
          />
          <HeaderNav.Item name={t('alert.label-plural')} linksTo="/alerts" active="/alerts" />
        </HeaderNav>
        <LogoutButton />
      </header>
    );
  }
}

export default withErrorHandling(withRouter(Header));
