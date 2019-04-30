/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// re-enable the tests once https://github.com/airbnb/enzyme/issues/1604 is fixed

// import React from 'react';
// import {mount} from 'enzyme';

// import Button from './Button';

// jest.mock('react-router-dom', () => {
//   return {
//     Redirect: ({to}) => {
//       return <div>REDIRECT to {to}</div>;
//     },
//     Link: ({children, to, onClick, id}) => {
//       return (
//         <a id={id} href={to} onClick={onClick}>
//           {children}
//         </a>
//       );
//     }
//   };
// });

// it('should render without crashing', () => {
//   mount(<Button />);
// });

// it('renders a <button> element by default', () => {
//   const node = mount(<Button />);

//   expect(node).toHaveDisplayName('Button');
// });

// it('renders a <a> element when specified as a property', () => {
//   const tag = 'a';

//   const node = mount(<Button tag={tag} />);
//   expect(node.find('.Button')).toHaveDisplayName('Link');
// });

// it('renders a label as provided as a property', () => {
//   const text = 'Click Me';

//   const node = mount(<Button>{text}</Button>);
//   expect(node.find('button')).toIncludeText(text);
// });

// it('renders a modifier class name based on the type provided as a property', () => {
//   const type = 'primary';
//   const node = mount(<Button type={type} />);

//   expect(node.find('button')).toHaveClassName('Button--primary');
// });

// it('renders a modifier class name based on the color provided as a property', () => {
//   const color = 'red';
//   const node = mount(<Button color={color} />);

//   expect(node.find('button')).toHaveClassName('Button--red');
// });

// it('renders the id as provided as a property', () => {
//   const id = 'my-button';

//   const node = mount(<Button id={id} />);
//   expect(node.find('button')).toMatchSelector('#' + id);
// });

// it('does render the title as provided as a property', () => {
//   const titleText = 'my-button';

//   const node = mount(<Button title={titleText} />);
//   expect(node.find('button')).toMatchSelector('button[title="' + titleText + '"]');
// });

// it('does merge and render classNames provided as a property', () => {
//   const node = mount(<Button className={'foo'} />);
//   expect(node.find('.Button')).toMatchSelector('.Button.foo');
// });

// it('adds an "is-active" class when "active" prop was provided', () => {
//   const node = mount(<Button active />);
//   expect(node.find('.Button')).toMatchSelector('.Button.is-active');
// });

// it('executes a click handler as provided as a property', () => {
//   const handler = jest.fn();
//   const node = mount(<Button onClick={handler} />);

//   node.find('button').simulate('click');
//   expect(handler).toHaveBeenCalled();
// });

it('has a test file', () => {});
