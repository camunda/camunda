# Migration tests

Migration tests use Docker environment and test containers to imitate real setup.

Upgrade path under test can be defined in `migration-test/src/test/resources/test.properties` file in `upgrade.path` property - array of older versions.
E.g. if `upgrade.path=1.2.0,1.2.1` and current project version is `0.24.0-SNAPSHOT`, 
then following migration will be tested: `1.2.0 -> 1.2.1 -> 0.24.0-SNAPSHOT`.

How migration test suite works:
1. Runs Elasticsearch as test container.
2. Reads `upgrade.path` property.
3. Iterates over versions and for each finds appropriate `TestFixture` implemetation and applies it. Normally test fixture would:
    * start corresponding Zeebe and Tasklist version as test containers
    * migrate older data that is already present in Elasticsearch (this step is optional as Tasklist can auto-migrate data on startup in the previous step)
    * generate data needed to test corresponding migrations
    * stop Tasklist and Zeebe
4. Runs assertions
5. Stops Elasticsearch