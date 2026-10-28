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

To count distinct DRG ids, or compare them across brokers, select only the id in a
`raw_*` query in its own pack (`raw_drg_ids` in [queries.md](queries.md)), with a
`filename_suffix` that starts with `raw_`. Run the starter pack first: `run_mat.sh` refuses
a `raw_` pack as a dump's first parse, so its log never becomes `raw/first_parse.log`. Hash each
distinct id with one salt per incident, never printed. Print only counts or hash prefixes,
then delete that pack's `raw/reports/<suffix>/`, `raw/<suffix>.zip` and
`raw/mat_<suffix>.log`.

Hash with `scripts/hash_ids.sh`. It stops when the salt or the CSV is missing, or when a
hash fails, and never prints a plain id.

```bash
# Once per incident. Never print the salt.
SALT_FILE=~/incidents/<id>/.id_salt
[ -s "$SALT_FILE" ] || (umask 077 && od -An -tx1 -N16 /dev/urandom | tr -d ' \n' >"$SALT_FILE")

# The raw_* pack's own folder, e.g. filename_suffix raw_ids, per broker
H=.agents/skills/heap-dump-analysis/scripts/hash_ids.sh
A=$OUT_DIR/<broker-a>/raw/reports/raw_ids
B=$OUT_DIR/<broker-b>/raw/reports/raw_ids
# && stops at the first failing hash, so an error never reads as a count
"$H" "$SALT_FILE" "$A"/raw_drg_ids[0-9]*.csv >"$OUT_DIR/a.hashes" &&
  "$H" "$SALT_FILE" "$B"/raw_drg_ids[0-9]*.csv >"$OUT_DIR/b.hashes" &&
  wc -l <"$OUT_DIR/a.hashes" &&                                 # distinct ids on A
  comm -3 "$OUT_DIR/a.hashes" "$OUT_DIR/b.hashes" | wc -l       # ids on one broker only
```

The hashes let you compare sets across brokers, and name an id in the report by its prefix.
