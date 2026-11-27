# Engine risks

A risk is an area of the engine where a change needs care to avoid regressions and bugs.
This folder holds one markdown file per documented risk.
The files are written for the people and the AI agents that change the engine.

Each risk is also an issue labelled `risk/engine`, with a title that starts with `[Engine Risk]`.
The file holds the content and is the source of truth: if the issue and the file disagree, the file is right.
The issue tracks the status, links and discussion.
How risks are tracked and moved through their statuses is described in the company handbook.

## Risk status and kind

Every risk has a status and a kind, in the front matter of its file (`status`, `kind`).
They are also fields of the Engine Governance project, which shows them on the risk catalog view. Keep the two equal: when you change one, change the other, and if they disagree the file is right.

The **status** tells you how well the risk is protected, so how much you rely on your own care:

| Status | Meaning |
|---|---|
| Documented | The file holds what is needed to avoid the mistake. Only human review protects against it, so follow the file closely. |
| Mitigated | Automated tests or checks stop the mistake from being merged. Still follow the file: the checks may not cover every case. |
| Resolved | The code was changed so the mistake cannot be written. The file is in `resolved/` and no longer applies. |

A risk that is only Discovered has an issue but no file yet.

The **kind** tells you what happens if the risk is not handled correctly:

| Kind | What happens |
|---|---|
| Correctness | Wrong behavior or data corruption |
| Backward compatibility | Rolling updates or existing data and clients break |
| Performance | Slowdown or resource exhaustion |
| Test coverage | Regressions go unnoticed |

A new kind is added when a risk does not fit.

## How to read the risks

Before you change an area of the engine, check which risks apply to it.
Do not read every file in full, and do not read only the first lines of each: the **front matter** of each file, the part between the two `---` lines at the top, has everything needed to decide whether the file concerns your change.
The body below it is additional detail.

1. List the files directly in this folder (not in `resolved/`) and read the front matter of each, for example `sed -n '2,/^---$/p' <file>`.
2. Compare `applies_when` and `does_not_apply_when` with what you are changing, using the `summary` to understand the risk.
3. If the file applies, or you are not sure, read the whole file and follow **What to do**.
   If it does not apply, stop reading that file.
4. If your change needs an exception, check **Exceptions**. Do not make one that is not listed there.
5. Make sure the tests and checks in **How to detect** pass, and add the ones the **Coverage bar** asks for if your change is the one that introduces them.

If a risk is unclear, or you found one that is missing, ask in [#top-c8-engine-governance](https://camunda.slack.com/archives/C0BPZFRT19N) or open an issue with the "Engine risk" template.

### Resolved risks

A risk is resolved when the code was changed so the mistake can no longer be written.
Its file moves to `resolved/`, with the same name, and stays there as history.

- Do not check your change against the files in `resolved/`. They no longer apply, and the same goes for reviewers and agents that review a pull request.
- Read them when you handle a new risk, or look for a structural fix: they show which risks we already handled and how.

## How to document a risk

Anyone can document a risk with a pull request that adds a file to this folder.

### Naming

`<issue-number>-<short-name>.md`: the number of the risk's issue, then a short kebab-case name.
For example `54823-released-record-properties.md`.
The number makes the file easy to match with its issue.
Files whose name starts with `_` are not risks.
When a risk is resolved, its file moves to `resolved/` and keeps its name.

### Format

Copy [`_template.md`](_template.md) and fill in every section.
The template explains what each section is for.

- The front matter is a structured header with the `title`, the link to the `issue`, the `status`, the `kind` (values above), and what a reader needs to decide whether the file applies: `summary`, `applies_when` and `does_not_apply_when`. Keep status and kind equal to the project fields when you change them.
- The title states the mistake, not the area. It is the title of the issue without the `[Engine Risk]` prefix.
- The front matter is the only part a reader is guaranteed to read. Make it sufficient to decide, and keep it short: a summary of two or three sentences, and `applies_when` specific enough to match against a diff. `does_not_apply_when` lets readers stop early on look-alike changes. Do not put anything needed for that decision in the body, and do not repeat the body in the front matter. The details follow in the body.
- The file must make sense on its own. Do not rely on the reader having seen the discussion in the issue or in Slack.

### Writing for contributors and agents

- Say what the reader must do or must not do, and keep it easy to find. The body may also hold context and background, such as how the risk came up, why the rule exists and what was tried before, when that helps the reader judge a case the rule does not cover.
- Be concrete: name modules, classes, files, tests and commands, not "the relevant code".
- Say what happens when the risk is not handled, so the reader can judge the severity.
- State the exceptions explicitly, including who approves them and how to ask. Write "None" if there are none.
- Link precedents (issues, pull requests, incidents) instead of retelling them.
- Write one sentence per line, as in the other documents in `docs/zeebe`.
