# Camunda Operate

**[Backend Documentation](./client)**

**[Frontend Documentation](./client)**

## Running locally

To run the application locally you can use `docker` and `docker-compose`.
Make sure to have a recent version of these tools installed locally: you
should be able to run these two commands on your shell without `sudo`.

If you need support to configure these tools please contact Andrea

To spawn a local environment, run this command in the root folder:

```
docker-compose up -d
```

This command should pull/build the necessary containers to run the
application locally, the first run might take a while. This includes
a local elasticsearch, zeebe, operate backend and frontend.

## Commit Message Guidelines

* **feat** (new feature for the user, not a new feature for build script)
* **fix** (bug fix for the user, not a fix to a build script)
* **docs** (changes to the documentation)
* **style** (changes to css, styling, etc; no business logic change)
* **refactor** (refactoring production code, eg. renaming a variable)
* **test** (adding missing tests, refactoring tests; no production code change)
* **chore** (updating grunt tasks etc; no production code change)
