# How mock requests?

## On the browser

Inside the the file `handlers.ts` we have an array with the same name where we can mock the response of each route like in the example below:

```ts
import {rest} from 'msw';
import {mockedResponse, mockField} from 'modules/mocks';

const handlers = [
  // Mocking the entire response
  rest.post('/url/path/i/want/to/mock', async (req, res, ctx) => {
    if (IS_NEW_FEATURE_ENABLED) {
      return res(ctx.json(mockedResponse));
    }

    const response = await ctx.fetch(req);

    // Makes a real request when the feature flag is disabled
    return res(ctx.json(await response.json()));
  }),
  // Mocking a piece of the response
  rest.get('/url/path/i/want/to/mock', async (req, res, ctx) => {
    const response = await ctx.fetch(req);
    const body = await response.json();

    return res(
      ctx.json({
        ...body,
        newField: mockField,
      })
    );
  }),
];

export {handlers};
```

## On Jest tests

For Jest tests we need to use a Node mock and we prefer to use `res.once` to have better control on how many times the endpoints are requested.

```ts
import {mockServer} from 'modules/mock-server/node';
import {mockedResponse} from 'modules/mocks';

// the endpoints can be mocked before each test or inside each test
mockServer.use(
  rest.get('/url/path/i/want/to/mock', (_, res, ctx) =>
    res.once(ctx.json(mockedResponse))
  )
);
```
