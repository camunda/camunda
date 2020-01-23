# Operate Frontend

> **Notice:** Make sure to have [yarn](https://yarnpkg.com/en/docs/install) installed in your machine

## Installation

```sh
yarn
```

You may need to run this command with administrator privileges, as the [postinstall script](./scripts/wireModules.js) creates a symlinked directory.

## Actions

### Start Development Server

```sh
yarn start
```

### Run tests

```sh
yarn test
```

### Run operate backend

While developing the frontend, you might need to have Elasticsearch, Zeebe and the backend up and running.
In this case you can run the following command in the root of this project:

```sh
make start-backend
```

You can then destroy the environment using:

```sh
make env-down
```

## Writing Components

Related files should be close to each other. React components usually consist of a directory with the following files:

- Component.js
- Component.test.js
- service.js
- service.test.js
- api.js
- index.js
- Readme.js

### Component.js

Should contain a default export with the Component. The component implementation should take care of everything directly related to rendering, lifecycle and state management. Complex operations or backend communication should not be in this file, but imported from the service.js file.

### Component.test.js

Should test the React component. All imports from the component under test should be mocked. We are using [jest](https://github.com/facebook/jest), [enzyme](https://github.com/airbnb/enzyme) as well as [jest-enzyme](https://github.com/FormidableLabs/enzyme-matchers).

### Component.setup.js

Should contain all static mock data which is used in Component.test.js.

### service.js

Should contain all business logic and manage backend communication for a component. This file should be independent from React.

### service.test.js

Should test the service. You should not need enzyme here as the service should be independent from React or any rendered component.

### constants.js

Should contain all constants which are used by the component. If constants are used by multiple components, consider putting it on a higher level or into `/modules/constants`.

### styled.js

Should contain all styled components. We're using [https://styled-components.com/](styled-components) for this. They can be imported and used in Component.js like this:

```js
import * as Styled from './styled'
...
<Styled.Button onClick={() => {}} />
```

### index.js

Defines the public interface of the directory. Usually re-exports the default component export as a named export. In some cases, it might also export other resources.

## Developing UI components

### The `src/modules` directory

Some components are used throughout the whole application. To avoid long parent chains when importing, like (`import Input from '../../../component/Input'`), please use `import Input from 'modules/components/Input'`.

> For readability, any global module in the `src/modules` directory should be imported relatively to `modules`.  
> e.g. "`import {post} from 'modules/request'`

### Styling normal React components

If you use the styled(MyComponent) notation and MyComponent does not render the passed-in className prop, then no styles will be applied. To avoid this issue, make sure your component attaches the passed-in className to a DOM node:

```js
class MyComponent extends React.Component {
  render() {
    // Attach the passed-in className to the DOM node
    return <div className={this.props.className} />;
  }
}
```

The safer way is to pass the whole props object:

```js
class MyComponent extends React.Component {
  render() {
    return <div {...props} />;
  }
}
```

Read more on [styled-components](https://www.styled-components.com/docs/advanced#styling-normal-react-components).

### Event handler naming

**For props**
When defining the prop names, prefix with on\*, as in onClick, onRemove, etc.
When having more detailed prop names, keep the verb at the end:
onItemClick
onItemDelete

**For component methods**
When passing down a function to a child component that contains logic related to an action:
use the handle\* prefix, as is handleClick, handleItemDelete.

As below, put the noun first (Alert), then the verb last (Click). Then, as other events pile up around that concept, they are grouped together nicely:

```js
onAlertClick={this.handleAlertClick}
onAlertHover={this.handleAlertHover}
```

If the method is passed further down the component tree use on* prefix. The *handle prefix is only used in the root component.

```js
onAlertClick={this.props.onAlertClick}
onAlertHover={this.props.onAlertHover}
```

### Boolean variables naming

For boolean variables please use the prefixes is* or has*, as an indicator for
the value stored and a better understanding of the code:

**bad**

```js
let withChildren = false;
```

**good**

```js
let hasChildren = false;
```

or

**bad**

```js
let visible = false;
...

if (visible) {
  <div> You can see me </div>
}
```

**good**

```js
let isVisible = false;
...

if (isVisible) {
  <div> You can see me </div>
}
```

Don't use negation in the variable name. It's hard to read.

**bad**

```js
let isNotAllowed = true;
```

**good**

```js
let isForbidden = true;
```

or

**bad**

```js
if (!IsNotAllowed) {
}
```

**good**

```js
if (isAllowed) {
}
```

### Forwarding Refs

Most UI components are React wrappers around some DOM elements. Those components should use [ref forwarding](https://reactjs.org/docs/forwarding-refs.html) to give the application access to the underlying element, e.g. for focus management.

## Testing

### Data test attributes

We use data-test attributes to select elements in our tests, as styled
components generates inconsistent classes, and deep nested selectors (nav div >
div:first-schild a) are not a solid solution.

When adding a new data-test attribute please follow the following conventions:

- start with a verb if the case (delete, confirm, add, etc...)
- add details about the area the element relates to (comment, diagram, account)
- end with the element type (button, title, tile, etc...)

The goal is to have specific attributes for a more ease of identifying elements,
and also to avoid name collision.

**Good**

```
<DiagramTile data-test="add-diagram-tile" />
```

**Good**

```
<Button data-test="delete-account-button" />
```

**Good**

```
<Button data-test="delete-comment-button" />
```

**Good**

For the button on the confirmation modal

```
<Button data-test="confirm-delete-comment-button" />
```

**Bad**

```
<Button data-test="delete-button" />
```

## Automatic Formatting

We use [prettier](https://prettier.io/) to automatically format the Javascript sourcecode. It automatically runs before any commit using [husky](https://github.com/typicode/husky) and [lint-staged](https://github.com/okonet/lint-staged). It is recommended to add a prettier plugin to your IDE:

- [VSCode](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode)

## Further Reading

This project was bootstrapped using [create-react-app](https://github.com/facebook/create-react-app), so if you want to find out how something related to the build process works, check out their [User Guide](https://github.com/facebook/create-react-app/blob/master/packages/react-scripts/template/README.md#table-of-contents).

## Accessibility

We care about #a11y! In order to cover basic requirements, we should make sure that:

- We use semantic markup
- Interactive elements can
  _ Be accessed using keyboard and screenreader
  _ Name/label are provided when necessary \* Can be actioned using mouse, Enter Key, or Space bar

We will further work on more detailed instructions and requirements, for now you can check

- The Accessibility section of our [Design Review Guidelines](https://app.camunda.com/confluence/display/camBPM/Design+Review+Guidelines)
- Our [Confluence page on Accessibility](https://app.camunda.com/confluence/display/camBPM/Accessibility)
- [Get started using VoiceOver, the Mac built-in screen reader](https://webaim.org/articles/voiceover/)
