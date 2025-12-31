
# Backporting commits in subdirectory versioning

## format-patch

git format-patch will export the git commits to a series of patch files that can be applied to a different branch or a different sub directory.

To export the last 3 commits as patch files, run:

```bash
git format-patch HEAD^3
```

New files will be created starting with `0001-`, `0002-`, etc.

## applying patch files without AI

Suppose you have a patch file `0001-refactor-web-modeler-make-webapp-memory-config-dynam.patch` that you want to apply to the `charts/camunda-platform-8.7` directory.

```bash
git apply -p3 --directory=charts/camunda-platform-8.7 0001-refactor-web-modeler-make-webapp-memory-config-dynam.patch
```

In many cases, this will apply cleanly, but if the command does not work, you can try using OpenCode or your editors AI integration.

## applying patch files with AI

If you have an AI tool integrated into your IDE, a query you can use is:

```
I have exported commits as patch files starting with 000*.patch using git format-patch. Please apply the patch to the charts/camunda-platform-8.7 directory.
```
