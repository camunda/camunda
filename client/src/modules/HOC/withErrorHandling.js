/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

export default function withErrorHandling(Component) {
  class WithErrorHandling extends React.Component {
    constructor(props) {
      super(props);

      this.mounted = true;

      this.state = {
        error: undefined,
      };
    }

    mightFail = async (retriever, cb, errorHandler) => {
      try {
        const response = await retriever;
        if (this.mounted) {
          return cb && cb(response);
        }
      } catch (error) {
        if (this.mounted) {
          errorHandler && errorHandler(error);
          this.setState({error});
        }
      }
    };

    componentWillUnmount() {
      this.mounted = false;
    }

    render() {
      return (
        <Component
          mightFail={this.mightFail}
          error={this.state.error}
          resetError={() => this.setState({error: false})}
          {...this.props}
        />
      );
    }
  }

  WithErrorHandling.displayName = `${
    Component.displayName || Component.name || 'Component'
  }ErrorHandler`;

  WithErrorHandling.WrappedComponent = Component;

  return WithErrorHandling;
}
