/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runLastEffect, runLastCleanup} from '__mocks__/react';
import React from 'react';
import withErrorHandling, {WithErrorHandlingProps} from './withErrorHandling';
import {shallow} from 'enzyme';

it('should pass the value of the receiver function to the callback', async () => {
  const spy = jest.fn();
  const Component = withErrorHandling(
    class extends React.Component<WithErrorHandlingProps> {
      render() {
        return (
          <button
            onClick={() =>
              this.props.mightFail(
                (async () => {
                  return 32;
                })(),
                spy
              )
            }
          />
        );
      }
    }
  );

  const node = shallow(<Component />);
  runLastEffect();

  node.dive().find('button').simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalledWith(32);
});

it('should return the value of the callback function from the mightfail', async () => {
  const spy = jest.fn();
  const Component = withErrorHandling(
    class extends React.Component<WithErrorHandlingProps> {
      render() {
        return (
          <button
            onClick={async () => {
              const result = await this.props.mightFail(
                (async () => {
                  return 32;
                })(),
                (value) => value
              );

              spy(result);
            }}
          />
        );
      }
    }
  );

  const node = shallow(<Component />);
  runLastEffect();

  node.dive().find('button').simulate('click');
  await node.update();
  await flushPromises();

  expect(spy).toHaveBeenCalledWith(32);
});

it('should not pass the value of the receiver function to the function when component is unmountd', async () => {
  const spy = jest.fn();
  const Component = withErrorHandling(
    class extends React.Component<WithErrorHandlingProps> {
      render() {
        return (
          <button
            onClick={() =>
              this.props.mightFail(
                (async () => {
                  return 32;
                })(),
                spy
              )
            }
          />
        );
      }
    }
  );

  const node = shallow(<Component />);
  runLastEffect();
  runLastCleanup();

  node.dive().find('button').simulate('click');
  await node.update();

  expect(spy).not.toHaveBeenCalled();
});

it('should catch errors', () => {
  const Component = withErrorHandling(
    class extends React.Component<WithErrorHandlingProps> {
      render() {
        return (
          <button
            onClick={() =>
              this.props.mightFail(
                (async () => {
                  throw new Error();
                })(),
                jest.fn()
              )
            }
          />
        );
      }
    }
  );

  const node = shallow(<Component />);
  runLastEffect();
  node.dive().find('button').simulate('click');
});

it('should pass an error and reset it via props', async () => {
  const error = new Error();
  const Component = withErrorHandling(
    class extends React.Component<WithErrorHandlingProps> {
      render() {
        return (
          <>
            <button
              onClick={() =>
                this.props.mightFail(
                  (async () => {
                    throw error;
                  })(),
                  jest.fn()
                )
              }
            />
            {this.props.error && (
              <div className="error">
                {this.props.error.toString()}
                <button className="errorBtn" onClick={() => this.props.resetError?.()} />
              </div>
            )}
          </>
        );
      }
    }
  );

  const node = shallow(<Component />);

  runLastEffect();
  node.dive().find('button').simulate('click');
  await node.update();

  expect(node.dive().find('.error')).toIncludeText(error.toString());

  node.dive().find('.errorBtn').simulate('click');
  expect(node.dive().find('.error')).not.toIncludeText(error.toString());
});

it('should call a custom error handler', async () => {
  const spy = jest.fn();
  const error = new Error();
  const Component = withErrorHandling(
    class extends React.Component<WithErrorHandlingProps> {
      render() {
        return (
          <button
            onClick={() =>
              this.props.mightFail(
                (async () => {
                  throw error;
                })(),
                jest.fn(),
                spy
              )
            }
          />
        );
      }
    }
  );

  const node = shallow(<Component />);

  runLastEffect();
  node.dive().find('button').simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalledWith(error);
});
