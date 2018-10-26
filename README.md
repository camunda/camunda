# Camunda Operate

**[Backend Documentation](./backend)**

**[Frontend Documentation](./client)**

## Running locally

To run the application locally you can use `docker`, `docker-compose` and
`make`. Make sure to have a recent version of these tools installed
locally: you should be able to run these commands on your shell without
`sudo`.

Windows users need to install `make` manually on their shell. You can find
instructions on how to do it
[here](https://gist.github.com/evanwill/0207876c3243bbb6863e65ec5dc3f058#make).

If you need support to configure these tools please contact Andrea or
Christian.

To spawn the full local environment, run this command in the root folder:

```
make env-up
```

The app will be running at `localhost:8080`.

To stop:

```
make env-down
```

To see the status of the environment, you can run:

```
make env-status
```

This command should pull/build the necessary containers to run the
application locally, the first run might take a while. This includes
a local elasticsearch, zeebe, operate backend and frontend.

You can clean your local docker environment using:

```
make env-clean
```

This will delete all dangling images and containers. It might be useful
when you run out of space or if you encounter any problem with the docker
daemon.

## Commit Message Guidelines

* **feat** (new feature for the user, not a new feature for build script)
* **fix** (bug fix for the user, not a fix to a build script)
* **docs** (changes to the documentation)
* **style** (changes to css, styling, etc; no business logic change)
* **refactor** (refactoring production code, eg. renaming a variable)
* **test** (adding missing tests, refactoring tests; no production code change)
* **chore** (updating grunt tasks etc; no production code change)
