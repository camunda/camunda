/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {ComponentType} from 'react';

export interface WithErrorHandlingProps {
  mightFail: (
    retriever: Promise<any>,
    cb: ((response: any) => void) | undefined,
    errorHandler?: ((error: any) => void) | undefined
  ) => void;
  error?: any;
  resetError?: () => void;
}

export default function withErrorHandling<P extends object>(Component: ComponentType<P>) {
  class WithErrorHandling extends React.Component<
    Omit<P, keyof WithErrorHandlingProps>,
    {error?: any}
  > {
    mounted = false;

    constructor(props: P) {
      super(props);
      this.state = {
        error: undefined,
      };
    }

    componentDidMount() {
      this.mounted = true;
    }

    componentWillUnmount() {
      this.mounted = false;
    }

    mightFail: WithErrorHandlingProps['mightFail'] = async (retriever, cb, errorHandler) => {
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

    resetError: WithErrorHandlingProps['resetError'] = () => {
      this.setState({error: undefined});
    };

    render() {
      const {error} = this.state;
      return (
        <Component
          mightFail={this.mightFail}
          error={error}
          resetError={this.resetError}
          {...(this.props as P)}
        />
      );
    }

    static displayName = `${Component.displayName || Component.name || 'Component'}ErrorHandler`;

    static WrappedComponent = Component;
  }

  return WithErrorHandling;
}
