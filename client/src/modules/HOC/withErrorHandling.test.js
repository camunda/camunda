/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import withErrorHandling from './withErrorHandling';

import {mount} from 'enzyme';

it('should pass the value of the receiver function to the callback', done => {
  const spy = jest.fn();
  const Component = withErrorHandling(
    class extends React.Component {
      constructor(props) {
        super(props);

        props.mightFail(32, spy);
      }
      render() {
        return null;
      }
    }
  );
  mount(<Component />);

  setTimeout(() => {
    expect(spy).toHaveBeenCalledWith(32);
    done();
  });
});

it('should not pass the value of the receiver function to the function when component is unmountd', done => {
  const spy = jest.fn();
  const Component = withErrorHandling(
    class extends React.Component {
      constructor(props) {
        super(props);

        props.mightFail(32, spy);
      }
      render() {
        return null;
      }
    }
  );
  const node = mount(<Component />);
  node.unmount();

  setTimeout(() => {
    expect(spy).not.toHaveBeenCalled();
    done();
  });
});

it('should catch errors', done => {
  const Component = withErrorHandling(
    class extends React.Component {
      constructor(props) {
        super(props);

        props.mightFail(
          (async () => {
            throw new Error();
          })(),
          jest.fn()
        );
      }
      render() {
        return null;
      }
    }
  );
  mount(<Component />);

  setTimeout(() => {
    done();
  });
});

it('should pass errors via props', done => {
  const error = new Error();
  const Component = withErrorHandling(
    class extends React.Component {
      constructor(props) {
        super(props);

        props.mightFail(
          (async () => {
            throw error;
          })(),
          jest.fn()
        );
      }
      render() {
        return (this.props.error && this.props.error.toString()) || null;
      }
    }
  );
  const node = mount(<Component />);

  setTimeout(() => {
    expect(node).toIncludeText(error.toString());
    done();
  });
});

it('should call a custom error handler', done => {
  const spy = jest.fn();
  const error = new Error();
  const Component = withErrorHandling(
    class extends React.Component {
      constructor(props) {
        super(props);

        props.mightFail(
          (async () => {
            throw error;
          })(),
          jest.fn(),
          spy
        );
      }
      render() {
        return null;
      }
    }
  );
  mount(<Component />);

  setTimeout(() => {
    expect(spy).toHaveBeenCalledWith(error);
    done();
  });
});
