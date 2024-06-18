# Restore dump

## Intro

This action abstracts installing postgres client and restoring the database dump to postgres database

## Usage

### Inputs

|          Input          |                                     Description                                      | Required |          Default          |
|-------------------------|--------------------------------------------------------------------------------------|----------|---------------------------|
| sql_dump                | Name of the dump file stored in `gs://optimize-data/`                                | false    | optimize_data-medium.sqlc |
| postgres_user           | Postgres username                                                                    | false    | camunda                   |
| postgres_db             | Postgres database                                                                    | false    | engine                    |
| num_of_parallel_threads | Number of parallel threads to use during the import. Use 0 to turn off parallel mode | false    | 8                         |

## Example of using the action

NOTE: This action requires gsuitil to run, you can use login-gcloud action (see example code below)

```yaml
steps:
  - name: Login to Google Cloud
    uses: ./.github/actions/login-gcloud
    with:
      secrets: ${{ toJSON(secrets) }}
  - name: Download and restore dump
    uses: ./.github/actions/restore-dump
    with:
      sql_dump: "optimize_data-e2e.sqlc"
```

