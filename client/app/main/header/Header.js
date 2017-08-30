import React from 'react';
import {getRouter} from 'router';
import {createViewUtilsComponentFromReact} from 'reactAdapter';
import {AppMenu} from './appMenu';

const jsx = React.createElement;

const router = getRouter();

export class HeaderReact extends React.Component {
  render() {
    return <header>
      <div className="navbar-header">
        <a href="/" className="navbar-brand" title="Camunda Optimize" onClick={this.goToRoot}>
          <span className="brand-logo"></span>
          &nbsp;
          <span className="brand-name">Camunda Optimize</span>
        </a>
      </div>
      <AppMenu />
    </header>;
  }

  goToRoot = (event) => {
    if (!this.props.redirect) {
      event.preventDefault();
    }

    router.goTo('default', {});
  }
}

export const Header = createViewUtilsComponentFromReact('div', HeaderReact);
