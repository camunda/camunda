# Build Platform Docker

This composite action builds Docker images for the Camunda platform using BuildKit and layer caching.

## Purpose

Builds optimized Docker images with efficient layer caching to reduce build time and costs in CI/CD pipelines.

## Inputs

| Name | Description | Required | Default |
|------|-------------|----------|---------|
| `repository` | The image repository, e.g., `camunda/zeebe` | Yes | `camunda/zeebe` |
| `version` | The image version, e.g., `SNAPSHOT`, `8.1.0`. Can accept a list of versions to create multiple tags for the same image. | No | Maven project version |
| `distball` | The path to the Camunda platform distribution TAR ball | No | `dist/target/camunda-zeebe-*.tar.gz` |
| `revision` | The revision of the source the content of this image is based on | No | `github.sha` |
| `push` | If true, will push the image | No | `false` |
| `platforms` | Comma-separated list of platforms to build the image for | No | `linux/amd64` |
| `dockerfile` | Path to the Dockerfile to use | No | `Dockerfile` |

## Outputs

| Name | Description |
|------|-------------|
| `image` | Fully qualified image name available in your local Docker daemon |
| `date` | The ISO 8601 date at which the image was created |
| `version` | The semantic version of the packaged artifact |

## Caching Strategy

This action leverages Docker BuildKit layer caching using GitHub Actions cache (`type=gha`) to significantly reduce build times:

- **Cache Scope**: Each Dockerfile has its own cache scope to prevent conflicts between different image builds
- **Cache Mode**: Uses `mode=max` to cache all intermediate layers, not just the final result
- **Branch Detection**: QEMU cache images are only stored on persistent branches (main and stable/*) where reuse is likely
- **Performance**: Intermediate stages like `base`, `jre-build`, and `java` are cached and reused across builds

### How It Works

1. **cache-from**: Restores previously cached layers matching the Dockerfile scope
2. **cache-to**: Stores all intermediate build layers to GitHub Actions cache for future builds
3. **Scope**: Cache is scoped by Dockerfile path, so `Dockerfile`, `operate.Dockerfile`, etc., maintain separate caches

This approach works on both GitHub-hosted and self-hosted runners without requiring external infrastructure.

## Example Usage

### Basic Usage (Build Only)

```yaml
- uses: ./.github/actions/build-platform-docker
  with:
    repository: camunda/zeebe
    version: SNAPSHOT
```

### Build and Push

```yaml
- uses: ./.github/actions/build-platform-docker
  with:
    repository: camunda/zeebe
    version: 8.1.0
    push: true
```

### Multi-Platform Build

```yaml
- uses: ./.github/actions/build-platform-docker
  with:
    repository: camunda/zeebe
    version: 8.1.0
    platforms: linux/amd64,linux/arm64
    push: true
```

### Custom Dockerfile

```yaml
- uses: ./.github/actions/build-platform-docker
  with:
    repository: camunda/operate
    dockerfile: operate.Dockerfile
    version: SNAPSHOT
```

## Prerequisites

- Code must be checked out beforehand (e.g., via `actions/checkout@v4`)
- For building from distball: Distribution tarball must exist at the specified path
- For multi-platform builds: Sufficient runner resources and time

## Permissions

This action requires:
- Read access to the repository
- Write access to GitHub Actions cache (automatically granted in workflows)

## Related Files

- `99apt-timeout-and-retries`: APT configuration for resilient package installation
- Root Dockerfiles: `Dockerfile`, `camunda.Dockerfile`, `operate.Dockerfile`, `tasklist.Dockerfile`, `optimize.Dockerfile`
