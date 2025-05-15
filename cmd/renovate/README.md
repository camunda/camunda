## Local Renovate Tests

If you want to tweak the [renovate config](../../.github/renovate.json), it's a good idea to test this locally.
You can make use of the script [renovate-local.sh](./renovate-local.sh) to do so.

First, [create a Github PAT](https://github.com/settings/tokens/new) with repo scope and assign it to a variable:

```shell
GITHUB_TOKEN=<yourPAT>
```

After that you can just execute `./renovate-local.sh` and it will take the LOCAL `.github/renovate.json` config as source of truth and execute a full dry run.
