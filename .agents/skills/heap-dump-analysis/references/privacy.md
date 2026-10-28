# Privacy: keep customer data out of the LLM

A broker heap holds customer data: variables, payloads, deployed models, ids, sometimes
secrets. Anything you read with a tool lands in the LLM context, so the agent sees
aggregates, never values. DRG ids, for example, are often customer or product names.

## Rules

- **Everything MAT writes stays in `raw/`** and is never read or printed: CSVs, logs, HTML,
  the workspace. The `<stem>.threads` file next to the dump counts as `raw/` too.
- **Extract only numbers and class names**, from CSVs whose columns are all ids, sizes,
  counts, numeric fields or class names, with one-liners that print aggregates
  ([queries.md](queries.md)). A `grep -c` on a log is fine; looking at lines is not.
- **Know the columns from the query** (SELECT order), not by printing a header or a row.
- **In queries**, select `x.@objectId`, `x.@retainedHeapSize`, `classof(x).@name`, numeric
  fields and counts. Not `x` (that prints MAT's label), not `toString(...)`.
- **Stack frames** may only be counted ([queries.md](queries.md)).
- **Subagents** get these rules and return numbers, class names and hash prefixes only.
- **Never upload or commit** dumps or outputs. Delete them when the incident is closed.
- **In the report**, never name customer ids. Say "DRG A".

## Comparing ids without seeing them

To count distinct DRG ids, or compare them across brokers, run
`queries/raw_drg_ids_pack.xml` after the starter pack. `run_mat.sh` refuses a `raw_` pack as
a dump's first parse, so its log never becomes `raw/first_parse.log`. For other ids, write
a pack like it: select only the id, and give the pack a `filename_suffix` that starts with
`raw_`.

Then:

1. **Create one salt per incident**: random bytes in a file only you can read, for example
   `~/incidents/<id>/.id_salt`. Never print it. Use the same salt for every broker, or the
   hashes don't match.
2. **Hash each broker's CSV** with
   `scripts/hash_ids.sh <salt file> raw/reports/raw_ids/raw_drg_ids<N>.csv`, into a file
   per broker outside `raw/`. It prints a 12-char hash per distinct id and never a plain id.
   If it fails, stop: its output is incomplete, and a count of it would be wrong.
3. **Compare the hash files**: count lines for distinct ids, and use `comm` for ids that are
   on one broker only. Name an id in the report by its hash prefix.
4. **Delete** that pack's `raw/reports/<suffix>/`, `raw/<suffix>.zip` and
   `raw/mat_<suffix>.log`.
