import React from 'react';
import ReactDOM from 'react-dom';
import {DESTROY_EVENT} from 'view-utils';

export function createViewUtilsComponentFromReact(tag, ReactComponent) {
  return ({children, ...props}) => {
    return (node, eventsBus) => {
      let listener;

      class AdapterComponent extends React.Component {
        constructor(props) {
          super(props);

          this.state = {};

          listener = this.setState.bind(this);
        }

        render() {
          return React.createElement(
            ReactComponent,
            {
              ...this.state,
              ...props
            },
            children
          );
        }
      }

      const target = document.createElement(tag);

      node.appendChild(target);
      ReactDOM.render(
        React.createElement(AdapterComponent),
        target
      );

      eventsBus.on(DESTROY_EVENT, () => ReactDOM.unmountComponentAtNode(target));

      return state => {
        if (listener && typeof listener === 'function') {
          listener(state);
        }
      };
    };
  };
}
