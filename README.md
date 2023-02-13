## Tasklist

Tasklist is an graphical and API application to manage user tasks in Zeebe.

### Running visual regression tests

On Tasklist we use Playwright for visual regression testing. These tests run on every push on every branch through Github Actions.

To run these locally you can follow the steps below:

1. Inside the client folder run `yarn build`
2. After the build is finished start the Docker container with `yarn start-visual-regression-docker`
3. Inside the container, run `yarn start:visual-regression &`
4. After that, run `yarn test:visual-regression`

If you made feature changes and want to purposely wants to update the UI baseline you can follow the steps before, but on step 4 you should run `yarn test:visual-regression --update-snapshots`. Beware the this will update all screenshots, so make sure you only have the changes you want to update in your branch.
