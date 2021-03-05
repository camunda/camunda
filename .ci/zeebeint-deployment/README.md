# Zeebe Integration Deployment

## Intro

This environment is an Optimize deployment based on the `prototype_zeebeint` branch created in ticket [INFRA-2139](https://jira.camunda.com/browse/INFRA-2139).

It can be used for experiments to test Optimize with Zeebe (both backed by CamBPM and Elasticsearch respectively, see `deployment.yml`) and is available at https://prototype-zeebeint.optimize.camunda.cloud/.

## Reset to state of `master` branch

```shell
git fetch origin
git checkout prototype_zeebeint
git reset --hard origin/master
git push --force
```
