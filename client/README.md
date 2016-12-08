# Camunda Optimize Frontend

## Installation

Install yarn
```bash
npm install -g yarn
```

Install dependencies
```bash
yarn
```

## Development server

```bash
yarn run serve
```

## Production

```bash
yarn run compile
```

Should create ``dist`` folder with builded application.

## Testing

To run tests single time:
```bash
yarn run test
```

Watch:
```bash
yarn run test-watch
```
## Components

### Design

Each components consists of 3 functions ``constructor``, ``template`` and ``update``.

``constructor`` is function that takes object with attributes used to create component. 
With special attribute ``children`` which is array of children of this component. It should return ``template`` function. 


``template`` is function that takes parent node and events bus. It here where all initial manipulation of DOM takes place. 
It should return ``update`` function.

``update`` is function that is called each time state of this component changes.
It should adjust DOM elements created by ``template`` function to reflect new state component.
 
Example of simple component:
```javascript
export function DomComponent({title}) { // constructor function
    return (parentNode, eventsBus) => { // template function
      const childNode = document.createElement('div');
      childNode.innerHTML = `<b>${title}</b> = <i></i>`;
      
      parentNode.appendChild(childNode);
      
      const nameNode = childNode.querySelector('i');
      
      return ({name}) => { // update function
        nameNode.innerHTML = name;
      }
    };
}
```

Project support also jsx syntax which always creates ``template`` function during complication.

Example of static component with jsx:

```javascript
import {jsx} from 'view-utils';

export function StaticJsxComponent({text}) { // constructor function
    return <div>{text}</div>; // template function
}
```

Dynamic component with jsx can be implemented 
with using more than one template on parent node and simple DOM manipulation.

```javascript
import {jsx} from 'view-utils';

export function DynamicJsxComponent({title}) { // constructor function
    const template = <div> // template function created by jsx
      <b>{title}</b> = <i></i>
    </div>;

    return (parentNode, eventsBus) => { // template function
      template(node, eventsBus); // use jsx template function
      
      const nameNode = childNode.querySelector('i');
      
      return ({name}) => { // update function
        nameNode.innerHTML = name;
      }
    };
}
```

You can also use helper components like ``List``, ``Match``, ``Select``, ``Text`` 
and so on create declarative description of changes.

```javascript
import {jsx} from 'view-utils';

export function DynamicJsxComponent2({title}) { // constructor function
    return <div> // template function created by jsx
      <b>{title}</b> = <i><Text property="name" /></i>
    </div>;
}
```

Nesting component is also simple just import it in your code and use with jsx.

```javascript
import {jsx} from 'view-utils';
import {DynamicJsxComponent2} from './whatever';

export function Parent() {
    return <div>
     <DynamicJsxComponent2 title="Kitty name" />
    </div>;
}
```

### Testing components

Add new file in test folder with path similar to component being test.
Test file name should end with ``.test.js``.

For testing use ``chai``, ``chai-dom`` and ``sinon``.
In ``test/testHelpers`` you can find functions that will be useful during testing. 
Like ``mountTemplate`` which adds your template to dom and 
return object with ``node``, ``update`` and ``eventsBus`` properties.
You can use ``node`` is parent node of your component. ``update`` is function
which updates your component you can pass mocked state to it and ``eventsBus`` is well events bus.




