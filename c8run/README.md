# Camunda 8 Run

C8 Run is a packaged distribution of Camunda 8, which allows you to spin up Camunda 8 within seconds.

Please refer to the [local installation with Camunda 8 Run guide](https://docs.camunda.io/docs/next/self-managed/setup/deploy/local/c8run/) for further details.

## Build C8Run locally

### Prerequisites

To successfully build `c8run`, you need:

- Go 1.21 or later
- Bash shell
- OKTA credentials for internal Camunda artifacts

### Build steps

Export your OKTA credentials as:

```bash
export JAVA_ARTIFACTS_USER=firstname.lastname
export JAVA_ARTIFACTS_PASSWORD=password
```

Execute the following commands from c8run directory:

```bash
go build -o c8run ./cmd/c8run/main.go
./package.sh
./start.sh
```

## CI requirement for merging

Only CI checks related to C8Run (those with "c8run" in the name) and CI runs marked as `required` are needed to merge. Non-C8Run-related CI checks can be ignored.