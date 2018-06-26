import React, {Component} from 'react';
import PropTypes from 'prop-types';

import {ThemeProvider, ThemeConsumer} from 'modules/theme';

export default class Wrapper extends Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  render() {
    return (
      <ThemeProvider>
        <ThemeConsumer>
          {({toggleTheme, theme}) => (
            <React.Fragment>
              <div style={{marginBottom: '1em'}}>
                <button onClick={toggleTheme}>Toggle Theme</button>
              </div>
              <div
                style={{
                  backgroundColor: theme === 'dark' ? '#1c1f23' : '#f2f3f5',
                  color: theme === 'dark' ? '#ffffff' : '#62626e',
                  padding: '1em'
                }}
              >
                {this.props.children}
              </div>
            </React.Fragment>
          )}
        </ThemeConsumer>
      </ThemeProvider>
    );
  }
}
