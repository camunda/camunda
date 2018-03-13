import React from 'react';

export default Component => {
  class WithErrorHandling extends React.Component {
    constructor(props) {
      super(props);

      this.mounted = true;

      this.state = {
        error: undefined
      };
    }

    mightFail = async (retriever, cb, errorHandler) => {
      try {
        const response = await retriever;
        if (this.mounted) {
          return cb(response);
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
      return <Component mightFail={this.mightFail} error={this.state.error} {...this.props} />;
    }
  }

  WithErrorHandling.displayName = `${Component.displayName ||
    Component.name ||
    'Component'}ErrorHandler`;

  return WithErrorHandling;
};
