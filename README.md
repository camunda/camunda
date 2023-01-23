## Tasklist

Tasklist is an graphical and API application to manage user tasks in Zeebe.

### Running visual regression tests

On Tasklist we use Playwright for visual regression testing. These tests run on every push on every branch through Github Actions.

To run these locally you can follow the steps below:

1. In the root folder run `make env-up`
2. In another terminal window run `make start-e2e`
3. In a 3rd terminal window and inside the folder `client/` run `yarn start-visual-regression-docker`. This starts a Docker container. This is necessary because, even though the tests always run on the same browser, each OS has slight UI differences on the same browsers.
4. Inside the Docker container run `yarn test:visual-regression`

If you made feature changes and want to purposely wants to update the UI baseline you can follow the steps before, but on step 4 you should run `yarn test:visual-regression --update-snapshots`. Beware the this will update all screenshots, so make sure you only have the changes you want to update in your branch.
